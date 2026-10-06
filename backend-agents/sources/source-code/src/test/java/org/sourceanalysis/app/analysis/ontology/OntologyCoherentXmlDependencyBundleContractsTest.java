package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.material.publish.EntryEvidenceReader;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.ClueKind;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitHandle;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Direct contracts for exact saved XML resource dependencies in coherent LINK bundles. */
final class OntologyCoherentXmlDependencyBundleContractsTest {
  private static final String ENTRY = "entry:" + "d".repeat(64);
  private static final String RESOURCE_PATH = "src/main/resources/mapper/AccountMapper.xml";
  private static final String STATEMENT_REF = RESOURCE_PATH + "#findAccount";
  private static final String DEPENDENCY_REF = "com.example.AccountResultMap";
  private static final String XML_SUBTREE =
      "<select id=\"findAccount\">SELECT account_id FROM account</select>";
  private static final String XML_SOURCE =
      "<?xml version=\"1.0\"?><mapper namespace=\"com.example.AccountMapper\">"
          + "<resultMap id=\"AccountResultMap\" type=\"Account\">"
          + "<id column=\"account_id\" property=\"id\"/></resultMap>"
          + "<select id=\"findAccount\">SELECT account_id FROM account</select></mapper>";

  private final ObjectMapper mapper = new ObjectMapper();
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  @Test
  void statementDependencyAddsTheExactSavedSameResourceFullXmlSource() {
    Fixture fixture = fixture(true, true);

    OntologyCoherentLinkBundle.Result result = prepare(fixture);

    assertThat(result.issueCode()).isNull();
    assertThat(result.packet().units())
        .anySatisfy(
            unit -> {
              assertThat(unit.kind()).isEqualTo(UnitKind.XML_RESOURCE);
              assertThat(unit.originalId()).isEqualTo(RESOURCE_PATH);
              assertThat(unit.content().path("rawSource").asText()).isEqualTo(XML_SOURCE);
            });
    int expectedSourceBytes =
        XML_SUBTREE.getBytes(StandardCharsets.UTF_8).length
            + XML_SOURCE.getBytes(StandardCharsets.UTF_8).length;
    assertThat(result.packet().cost().fullSourceBytes()).isEqualTo(expectedSourceBytes);
    UnitHandle resource =
        fixture.corpus().entryUnits(ENTRY, 0, 100).items().stream()
            .filter(unit -> unit.kind() == UnitKind.XML_RESOURCE)
            .findFirst()
            .orElseThrow();
    assertThat(
            hasUse(
                result.decision().path("derivedUses"),
                fixture.corpus().aliases().unitRef(resource),
                fixture.entryRef()))
        .isTrue();
  }

  @Test
  void resourceDependenciesUseOnlyExplicitSavedPathsAndDoNotRecurse() {
    Fixture fixture = fixture(true, true, true);
    OntologyCoherentLinkBundle.Result result = prepare(fixture);
    assertThat(
            result.packet().units().stream()
                .filter(unit -> unit.kind() == UnitKind.XML_RESOURCE)
                .map(unit -> unit.content().path("resourcePath").asText()))
        .containsExactlyInAnyOrder(RESOURCE_PATH, "src/main/resources/mapper/Shared.xml");
    assertThat(result.decision().path("requiredButUnread")).isEmpty();
  }

  @Test
  void statementWithoutDependenciesDoesNotDefaultToTheFullXmlResource() {
    Fixture fixture = fixture(false, true);

    OntologyCoherentLinkBundle.Result result = prepare(fixture);

    assertThat(result.issueCode()).isNull();
    assertThat(result.packet().units()).noneMatch(unit -> unit.kind() == UnitKind.XML_RESOURCE);
    assertThat(result.packet().cost().fullSourceBytes())
        .isEqualTo(XML_SUBTREE.getBytes(StandardCharsets.UTF_8).length);
  }

  @Test
  void missingDependencyResourceRemainsNamedUnreadWithoutFabricatedUnitOrEntryUse() {
    Fixture fixture = fixture(true, false);

    OntologyCoherentLinkBundle.Result result = prepare(fixture);

    assertThat(result.packet()).isNotNull();
    assertThat(result.packet().units()).noneMatch(unit -> unit.kind() == UnitKind.XML_RESOURCE);
    JsonNode requiredButUnread = result.decision().path("requiredButUnread");
    assertThat(requiredButUnread).isNotEmpty();
    assertThat(requiredButUnread.toString()).contains(RESOURCE_PATH, DEPENDENCY_REF);
    assertThat(requiredButUnread.findValues("unitRef")).isEmpty();
    assertThat(requiredButUnread.findValues("entryRef"))
        .allSatisfy(ref -> assertThat(ref.asText()).isEqualTo(fixture.entryRef()));
    assertThat(fixture.corpus().entryUnits(ENTRY, 0, 100).items())
        .noneMatch(unit -> unit.kind() == UnitKind.XML_RESOURCE);
  }

  private OntologyCoherentLinkBundle.Result prepare(Fixture fixture) {
    OntologyScopeReader.Task task =
        new OntologyScopeReader.Task(
            "T_LINK",
            OntologyScopeReader.TaskKind.LINK,
            OntologyScopeReader.ReadingMode.TECHNICAL_BUNDLE,
            List.of(fixture.use()),
            List.of(),
            List.of(fixture.anchorRef()));
    OntologyScopeReader.Question question =
        new OntologyScopeReader.Question(
            "Q1",
            "Follow only the saved statement dependency for this account mapping.",
            List.of(fixture.entryRef()),
            List.of(fixture.anchorRef()),
            List.of(task));
    return OntologyCoherentLinkBundle.prepare(fixture.corpus(), question, task, 100_000, 500_000);
  }

  private boolean hasUse(JsonNode uses, String unitRef, String entryRef) {
    for (JsonNode use : uses) {
      if (unitRef.equals(use.path("unitRef").asText())
          && entryRef.equals(use.path("entryRef").asText())) {
        return true;
      }
    }
    return false;
  }

  private Fixture fixture(boolean hasDependency, boolean includeResource) {
    return fixture(hasDependency, includeResource, false);
  }

  private Fixture fixture(
      boolean hasDependency, boolean includeResource, boolean externalDependency) {
    ObjectNode index = mapper.createObjectNode();
    index.putObject("header").putObject("sourceBasis").put("kind", "PREPARED_V1");
    ObjectNode entry = mapper.createObjectNode();
    entry.put("entryId", ENTRY);
    entry.putObject("sourceBasis").put("kind", "PREPARED_V1");
    entry.put("assemblyStatus", "ASSEMBLED");
    entry.putObject("entry").put("method", "GET").put("route", "/accounts/{id}");
    ObjectNode frontend = entry.putObject("frontend");
    frontend.putArray("units");
    frontend.putArray("requestUses");
    frontend.putArray("candidateRequestUses");
    ObjectNode java = entry.putObject("java");
    java.putArray("methods");
    java.putArray("calls");
    ObjectNode persistence = entry.putObject("persistence");
    persistence.putArray("bindings");
    ObjectNode statement = persistence.putArray("statements").addObject();
    statement.put("statementRef", STATEMENT_REF);
    statement.put("resourceRef", RESOURCE_PATH);
    statement.put("namespace", "com.example.AccountMapper");
    statement.put("statementId", "findAccount");
    statement.put("statementKind", "SELECT");
    statement.putNull("databaseId");
    statement.put("xmlSubtree", XML_SUBTREE);
    if (hasDependency) {
      statement
          .putArray("dependencyRefs")
          .addObject()
          .put("kind", "RESULT_MAP")
          .put("reference", DEPENDENCY_REF)
          .put("resolution", "RESOLVED");
    } else {
      statement.putArray("dependencyRefs");
    }
    persistence.putArray("sqlAnalyses");
    if (includeResource) {
      ObjectNode resource = persistence.putArray("resources").addObject();
      resource.put("resourcePath", RESOURCE_PATH);
      resource.put("namespace", "com.example.AccountMapper");
      resource.put("rawSource", XML_SOURCE);
      resource.putArray("dependencyResourcePaths");
      if (externalDependency) {
        resource.withArray("dependencyResourcePaths").add("src/main/resources/mapper/Shared.xml");
        ObjectNode shared = persistence.withArray("resources").addObject();
        shared.put("resourcePath", "src/main/resources/mapper/Shared.xml");
        shared.put("namespace", "com.example.Shared");
        shared.put(
            "rawSource",
            "<mapper namespace=\"com.example.Shared\"><sql id=\"fields\">id</sql></mapper>");
        shared.putArray("dependencyResourcePaths").add("not-read-recursively.xml");
      }
    } else {
      persistence.putArray("resources");
    }
    persistence.putArray("diagnostics");
    entry.putArray("sourceRefs");
    OntologyEvidenceCorpus corpus =
        OntologyEvidenceCorpus.fromVerifiedDirectory(
            new EntryEvidenceReader.Directory(
                json.encodeCanonical(index),
                ImmutableBytes.copyOf(new byte[0]),
                List.of(
                    new EntryEvidenceReader.EntryDocument(ENTRY, json.encodeCanonical(entry)))));
    UnitHandle statementHandle =
        corpus.entryUnits(ENTRY, 0, 100).items().stream()
            .filter(unit -> unit.kind() == UnitKind.XML_STATEMENT)
            .findFirst()
            .orElseThrow();
    String entryRef = corpus.aliases().entryRef(ENTRY);
    String anchorRef = corpus.aliases().clueRef(ClueKind.STATEMENT, statementHandle.originalId());
    OntologyScopeReader.UnitUse use =
        new OntologyScopeReader.UnitUse(corpus.aliases().unitRef(statementHandle), entryRef);
    return new Fixture(corpus, entryRef, anchorRef, use);
  }

  private record Fixture(
      OntologyEvidenceCorpus corpus,
      String entryRef,
      String anchorRef,
      OntologyScopeReader.UnitUse use) {}
}
