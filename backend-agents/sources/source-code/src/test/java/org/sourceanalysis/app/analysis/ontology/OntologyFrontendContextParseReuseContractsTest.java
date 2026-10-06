package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.material.publish.EntryEvidenceReader;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.FrontendContextSource;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitHandle;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

final class OntologyFrontendContextParseReuseContractsTest {
  private static final String ENTRY_ID = "entry:" + "7".repeat(64);
  private static final String CONTEXT_ID = "context:records";
  private static final String FIRST_UNIT_ID = "source:page";
  private static final String SECOND_UNIT_ID = "source:component";
  private final ObjectMapper mapper = new ObjectMapper();
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  @Test
  void eachMatchingFrontendUnitIsReadOnceAndContextOrderIsPreserved() {
    Fixture fixture = fixture(false, false);
    Map<UnitHandle, Integer> readCounts = new LinkedHashMap<>();

    List<FrontendContextSource> resolved =
        fixture
            .corpus()
            .frontendContextSources(fixture.context(), contentReader(fixture, readCounts));

    assertThat(resolved)
        .containsExactly(
            new FrontendContextSource("page-source", fixture.firstUnit()),
            new FrontendContextSource("component-source", fixture.secondUnit()));
    assertThat(readCounts)
        .hasSize(2)
        .containsEntry(fixture.firstUnit(), 1)
        .containsEntry(fixture.secondUnit(), 1);
  }

  @Test
  void emptyContextDoesNotReadAnyFrontendUnit() {
    Fixture fixture = fixture(true, false);
    Map<UnitHandle, Integer> readCounts = new LinkedHashMap<>();

    List<FrontendContextSource> resolved =
        fixture
            .corpus()
            .frontendContextSources(fixture.context(), contentReader(fixture, readCounts));

    assertThat(resolved).isEmpty();
    assertThat(readCounts).isEmpty();
  }

  @Test
  void invalidFirstContextIdentityFailsBeforeReadingFrontendUnits() {
    Fixture fixture = fixture(false, true);
    Map<UnitHandle, Integer> readCounts = new LinkedHashMap<>();

    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                fixture
                    .corpus()
                    .frontendContextSources(fixture.context(), contentReader(fixture, readCounts)))
        .withMessage("ONTOLOGY_PAGE_CONTEXT_SOURCE_UNIT_MISSING");
    assertThat(readCounts).isEmpty();
  }

  private Function<UnitHandle, JsonNode> contentReader(
      Fixture fixture, Map<UnitHandle, Integer> readCounts) {
    return handle -> {
      readCounts.merge(handle, 1, Integer::sum);
      return fixture.corpus().read(handle.entryId(), handle.kind(), handle.originalId()).content();
    };
  }

  private Fixture fixture(boolean emptyContext, boolean invalidFirstIdentity) {
    String firstPath = "web/pages/RecordPage.vue";
    String secondPath = "web/components/RecordRow.ts";
    String firstHash = "a".repeat(64);
    String secondHash = "b".repeat(64);
    String firstText = "export default { name: 'RecordPage' };";
    String secondText = "export function renderRecord(value) { return value; }";

    ObjectNode entry = mapper.createObjectNode();
    entry.put("entryId", ENTRY_ID);
    entry.putObject("sourceBasis").put("kind", "PREPARED_V1");
    entry.put("assemblyStatus", "ASSEMBLED");
    entry.putObject("entry").put("method", "GET").put("route", "/records");
    entry
        .putObject("java")
        .putArray("methods")
        .addObject()
        .put("methodKey", "method:records")
        .put("name", "records")
        .putObject("source")
        .put("text", "public void records() {}");
    ((ObjectNode) entry.path("java")).putArray("calls");
    ObjectNode frontend = entry.putObject("frontend");
    frontend.putArray("units").add(frontendUnit(FIRST_UNIT_ID, firstPath, firstHash, firstText));
    ((com.fasterxml.jackson.databind.node.ArrayNode) frontend.path("units"))
        .add(frontendUnit(SECOND_UNIT_ID, secondPath, secondHash, secondText));
    ObjectNode context = frontend.putArray("pageContexts").addObject();
    context.put("contextId", CONTEXT_ID);
    context.put("pagePath", firstPath);
    var sourceUnits = context.putArray("sourceUnits");
    if (invalidFirstIdentity) {
      sourceUnits.addObject().put("unitRef", " ");
    } else if (!emptyContext) {
      addContextUnit(
          sourceUnits.addObject(), "page-source", firstPath, firstHash, "PAGE", firstText);
      addContextUnit(
          sourceUnits.addObject(),
          "component-source",
          secondPath,
          secondHash,
          "COMPONENT",
          secondText);
    }
    entry.putObject("persistence").putArray("statements");
    entry.putArray("sourceRefs");

    ObjectNode index = mapper.createObjectNode();
    index.putObject("header").putObject("sourceBasis").put("kind", "PREPARED_V1");
    OntologyEvidenceCorpus corpus =
        OntologyEvidenceCorpus.fromVerifiedDirectory(
            new EntryEvidenceReader.Directory(
                json.encodeCanonical(index),
                ImmutableBytes.copyOf(new byte[0]),
                List.of(
                    new EntryEvidenceReader.EntryDocument(ENTRY_ID, json.encodeCanonical(entry)))));
    return new Fixture(corpus);
  }

  private ObjectNode frontendUnit(String id, String path, String sha256, String text) {
    ObjectNode unit = mapper.createObjectNode();
    unit.put("sourceUnitId", id);
    unit.put("path", path);
    unit.put("sourceSha256", sha256);
    unit.put("sourceUnitKind", path.endsWith(".vue") ? "PAGE" : "COMPONENT");
    unit.put("text", text);
    unit.set("sourceUnitRange", sourceRange(text.length()));
    return unit;
  }

  private void addContextUnit(
      ObjectNode use, String unitRef, String path, String sha256, String kind, String text) {
    use.put("unitRef", unitRef);
    use.put("sourcePath", path);
    use.put("sourceSha256", sha256);
    use.put("sourceUnitKind", kind);
    use.set("sourceUnitRange", sourceRange(text.length()));
  }

  private ObjectNode sourceRange(int length) {
    ObjectNode range = mapper.createObjectNode();
    range.put("offsetUtf16", 0);
    range.put("lengthUtf16", length);
    return range;
  }

  private record Fixture(OntologyEvidenceCorpus corpus) {
    private UnitHandle context() {
      return new UnitHandle(ENTRY_ID, UnitKind.FRONTEND_PAGE_CONTEXT, CONTEXT_ID);
    }

    private UnitHandle firstUnit() {
      return new UnitHandle(ENTRY_ID, UnitKind.FRONTEND_UNIT, FIRST_UNIT_ID);
    }

    private UnitHandle secondUnit() {
      return new UnitHandle(ENTRY_ID, UnitKind.FRONTEND_UNIT, SECOND_UNIT_ID);
    }
  }
}
