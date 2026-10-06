package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Four-file-only overview: no model, customer source, CDN or legacy preview mutation. */
final class OntologyBusinessOverviewRendererTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  @Test
  void offlineOverviewKeepsRolesDirectionConfidenceIsolatedObjectsAndTraceability() {
    byte[] unsafeBundle = "console.log('</script>')".getBytes(StandardCharsets.UTF_8);
    OntologyBusinessOverviewRenderer renderer =
        new OntologyBusinessOverviewRenderer(() -> ImmutableBytes.copyOf(unsafeBundle));

    String html = renderer.render(ontology(), coverage(), sourceIndex(), review());

    assertThat(html).contains("flowchart LR", "主业务对象", "支撑对象", "待确认对象");
    assertThat(html).contains("订单", "客户", "孤立对象", "保存引用", "推断对应");
    assertThat(html).contains("--&gt;", "-.-&gt;");
    assertThat(html)
        .contains(
            "src=\"data:text/javascript;base64,"
                + Base64.getEncoder().encodeToString(unsafeBundle)
                + "\"");
    // Mermaid 11.12 SVG text mode leaves our numeric label entities visible as literal text.
    // Its strict HTML label mode decodes them without creating elements from encoded label data.
    assertThat(html).contains("securityLevel: 'strict'", "htmlLabels: true", "startOnLoad: false");
    assertThat(html).doesNotContain("cdn.jsdelivr", "unpkg.com", "<script>alert(1)</script>");
    assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    assertThat(html).contains("ontology-source:fixture", "src/OrderService.java", "S1");
    assertThat(html).contains("NOT_REQUESTED", "REQUIRED_UNREAD", "MODEL_NOT_ADDRESSED");
    assertThat(html).contains("NOT_REVIEWED");
  }

  @Test
  void rendererRejectsUnknownPublicVersionInsteadOfGuessingFromFields() {
    ObjectNode invalid = (ObjectNode) json.parseCanonical(ontology());
    invalid.put("schemaVersion", "ontology-v1");
    OntologyBusinessOverviewRenderer renderer =
        new OntologyBusinessOverviewRenderer(() -> bytes("offline-bundle"));

    assertThatThrownBy(
            () ->
                renderer.render(json.encodeCanonical(invalid), coverage(), sourceIndex(), review()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ONTOLOGY_BUSINESS_OVERVIEW_INPUT_INVALID");
  }

  @Test
  void confirmedMechanismStaysSolidWhenOnlyCardinalityOrStateDetailIsUnknown() {
    ObjectNode document = (ObjectNode) json.parseCanonical(ontology());
    ObjectNode confirmed = (ObjectNode) document.path("linkTypes").get(0);
    confirmed
        .withArray("unknowns")
        .addObject()
        .put("field", "cardinality")
        .put("reason", "The selected source does not establish cardinality.");
    String html =
        new OntologyBusinessOverviewRenderer(() -> bytes("offline-bundle"))
            .render(json.encodeCanonical(document), coverage(), sourceIndex(), review());

    assertThat(graphSource(html)).contains(" -->|\"保存引用\"|");
    assertThat(html).contains("The selected source does not establish cardinality.");
    assertThat(graphSource(html)).contains(" -.->|\"推断对应\"|");
  }

  @Test
  void knownAssemblyCounterevidenceKeepsConfirmedMechanismDashed() {
    ObjectNode report = (ObjectNode) json.parseCanonical(review());
    report
        .withArray("assemblyIssues")
        .addObject()
        .putArray("definitionRefs")
        .add("ontology-link:confirmed");
    String html =
        new OntologyBusinessOverviewRenderer(() -> bytes("offline-bundle"))
            .render(ontology(), coverage(), sourceIndex(), json.encodeCanonical(report));

    assertThat(graphSource(html)).contains(" -.->|\"保存引用\"|");
    assertThat(graphSource(html)).doesNotContain(" -->|\"保存引用\"|");
  }

  @Test
  void rendererUsesActualV2AssemblerSourceAndUnknownFieldsWithoutReinterpretingThem() {
    OntologyScopedAssembler.FormalAssembly assembly =
        new OntologyBusinessLinkAssemblyV2ContractsTest().assembledFourFiles();
    String html =
        new OntologyBusinessOverviewRenderer(() -> bytes("offline-bundle"))
            .render(
                assembly.ontology(),
                assembly.coverage(),
                assembly.sourceIndex(),
                assembly.review());
    String sourceJsonl =
        new String(assembly.sourceIndex().copyToByteArray(), StandardCharsets.UTF_8);
    String actualSourceRef =
        json.parseCanonical(assembly.ontology())
            .path("objectTypes")
            .get(0)
            .path("evidenceRefs")
            .get(0)
            .asText();
    String actualUnknown =
        json.parseCanonical(assembly.ontology())
            .path("objectTypes")
            .get(0)
            .path("unknowns")
            .get(0)
            .path("reason")
            .asText();

    assertThat(sourceJsonl).contains("\"schemaVersion\":\"ontology-source-v1\"");
    assertThat(sourceJsonl).contains(actualSourceRef, "\"localRef\":\"S1\"");
    assertThat(html).contains(actualSourceRef, actualUnknown, "method:fixture");
    assertThat(html).contains("MODEL_NOT_ADDRESSED", "K2", "ontology-link:");
    assertThat(html).doesNotContain("cdn.jsdelivr", "unpkg.com");
  }

  @Test
  void reviewedCanonicalIdentityHasOneGraphBoxWithoutDiscardingOriginalDefinitions() {
    ObjectNode document = (ObjectNode) json.parseCanonical(ontology());
    ObjectNode original = (ObjectNode) document.path("objectTypes").get(0);
    original.put("canonicalObjectRef", "ontology-object:order");
    ObjectNode enrichment = original.deepCopy();
    enrichment.put("globalId", "ontology-object:order-enrichment");
    enrichment.put("name", "已审细化订单");
    ((ArrayNode) document.path("objectTypes")).add(enrichment);
    ((ObjectNode) document.path("linkTypes").get(0))
        .put("fromObjectRef", "ontology-object:order-enrichment");
    String html =
        new OntologyBusinessOverviewRenderer(() -> bytes("offline-bundle"))
            .render(json.encodeCanonical(document), coverage(), sourceIndex(), review());
    String graph = graphSource(html);

    assertThat(graph).doesNotContain("已审细化订单");
    assertThat(graph).contains("保存引用", "孤立对象");
    assertThat(graph.lines().filter(line -> line.contains("[\"")).count()).isEqualTo(6);
    assertThat(html).contains("已审细化订单", "ontology-object:order-enrichment");
  }

  @Test
  void overviewRefusesAnExplicitCanonicalTargetThatIsNotAnActualObject() {
    ObjectNode document = (ObjectNode) json.parseCanonical(ontology());
    ((ObjectNode) document.path("objectTypes").get(0))
        .put("canonicalObjectRef", "ontology-object:missing");
    OntologyBusinessOverviewRenderer renderer =
        new OntologyBusinessOverviewRenderer(() -> bytes("offline-bundle"));

    assertThatThrownBy(
            () ->
                renderer.render(
                    json.encodeCanonical(document), coverage(), sourceIndex(), review()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ONTOLOGY_BUSINESS_OVERVIEW_INPUT_INVALID");
  }

  @Test
  void graphLabelsRetainLongChineseConditionsPunctuationAndEveryMechanismWithoutSyntaxInjection() {
    String longName = "订单(含税金额>0) [状态=A/B] \" ] --> n999[伪造] " + "甲".repeat(80);
    String firstMechanism = "条件一：金额>0（含税）";
    String secondMechanism = "条件二：[状态=A/B] + 审批";
    ObjectNode ontology = (ObjectNode) json.parseCanonical(ontology());
    ((ObjectNode) ontology.path("objectTypes").get(0)).put("name", longName);
    ObjectNode confirmed = (ObjectNode) ontology.path("linkTypes").get(0);
    ArrayNode mechanisms = confirmed.putArray("mechanism");
    mechanisms.addObject().put("description", firstMechanism);
    mechanisms.addObject().put("description", secondMechanism);
    String html =
        new OntologyBusinessOverviewRenderer(() -> bytes("offline-bundle"))
            .render(json.encodeCanonical(ontology), coverage(), sourceIndex(), review());
    String rawGraph = graphSource(html);
    String decodedGraph = decodeMermaidEntities(rawGraph);

    assertThat(decodedGraph).contains(longName, firstMechanism, secondMechanism);
    assertThat(rawGraph).contains("#34;", "#93;", "#62;", "#40;", "#41;");
    assertThat(rawGraph).doesNotContain("\" ] --> n999[");
    assertThat(html).contains("条件一：金额&gt;0（含税）", secondMechanism);
    assertThat(html).doesNotContain("<script>alert(1)</script>");
  }

  private static String graphSource(String html) {
    String start = "<pre class=\"mermaid\" id=\"ontology-business-graph\">";
    int from = html.indexOf(start);
    int to = html.indexOf("</pre>", from);
    assertThat(from).isNotNegative();
    assertThat(to).isGreaterThan(from);
    return html.substring(from + start.length(), to)
        .replace("&quot;", "\"")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&");
  }

  private static String decodeMermaidEntities(String graph) {
    Matcher matcher = Pattern.compile("#([0-9]+);").matcher(graph);
    StringBuffer decoded = new StringBuffer();
    while (matcher.find()) {
      int codePoint = Integer.parseInt(matcher.group(1));
      matcher.appendReplacement(
          decoded, Matcher.quoteReplacement(new String(Character.toChars(codePoint))));
    }
    matcher.appendTail(decoded);
    return decoded.toString();
  }

  private ImmutableBytes ontology() {
    ObjectNode root = mapper.createObjectNode();
    root.put("schemaVersion", "ontology-v2");
    root.put("publicationStatus", "DRAFT_REVIEWABLE");
    ArrayNode objects = root.putArray("objectTypes");
    object(
        objects,
        "ontology-object:order",
        "订单\"><script>alert(1)</script>",
        "MAIN",
        "ontology-source:fixture");
    object(objects, "ontology-object:customer", "客户", "SUPPORT", "ontology-source:fixture");
    object(
        objects,
        "ontology-object:isolated",
        "孤立对象",
        "TECHNICAL_OR_UNKNOWN",
        "ontology-source:fixture");
    ArrayNode links = root.putArray("linkTypes");
    link(
        links,
        "ontology-link:confirmed",
        "ontology-object:order",
        "ontology-object:customer",
        "CONFIRMED",
        "保存引用");
    link(
        links,
        "ontology-link:inferred",
        "ontology-object:customer",
        "ontology-object:isolated",
        "INFERRED",
        "推断对应");
    root.putArray("operations");
    root.putArray("rules");
    root.putArray("dimensions");
    root.putArray("measures");
    root.putArray("metrics");
    return json.encodeCanonical(root);
  }

  private void object(ArrayNode objects, String ref, String name, String role, String source) {
    ObjectNode object = objects.addObject();
    object.put("globalId", ref);
    object.put("name", name);
    object.put("definition", "已审对象定义");
    object.put("certainty", "CONFIRMED");
    object.put("displayRole", role);
    object.putArray("evidenceRefs").add(source);
    object.putArray("unknowns");
  }

  private void link(
      ArrayNode links, String ref, String from, String to, String certainty, String mechanism) {
    ObjectNode link = links.addObject();
    link.put("globalId", ref);
    link.put("name", mechanism);
    link.put("definition", "具体业务对应");
    link.put("fromObjectRef", from);
    link.put("toObjectRef", to);
    link.put("certainty", certainty);
    link.putArray("mechanism").addObject().put("description", mechanism);
    link.putArray("evidenceRefs").add("ontology-source:fixture");
    link.putArray("unknowns");
  }

  private ImmutableBytes coverage() {
    ObjectNode root = mapper.createObjectNode();
    root.put("schemaVersion", "ontology-coverage-v3");
    root.put("coverageStatus", "INCOMPLETE");
    ArrayNode layers = root.putArray("recognitionLayers");
    layers
        .addObject()
        .put("layer", "OBJECT")
        .put("status", "COMPLETE_FOR_DECLARED_SCOPE")
        .putArray("taskRefs");
    layers.addObject().put("layer", "RELATION").put("status", "INCOMPLETE").putArray("taskRefs");
    layers.addObject().put("layer", "ACTION").put("status", "NOT_REQUESTED").putArray("taskRefs");
    layers.addObject().put("layer", "ANALYTIC").put("status", "NOT_REQUESTED").putArray("taskRefs");
    root.putArray("readingDispositions")
        .addObject()
        .put("taskId", "T-later")
        .put("unitRef", "U2")
        .put("status", "REQUIRED_UNREAD")
        .put("reason", "Source not sent to model.");
    root.putArray("taskDispositions");
    root.putArray("clueDispositions")
        .addObject()
        .put("clueRef", "K2")
        .put("outcome", "MODEL_NOT_ADDRESSED")
        .put("reason", "MODEL_NOT_ADDRESSED");
    return json.encodeCanonical(root);
  }

  private ImmutableBytes sourceIndex() {
    ObjectNode source = mapper.createObjectNode();
    source.put("schemaVersion", "ontology-source-v1");
    source.put("sourceRef", "ontology-source:fixture");
    source.put("localRef", "S1");
    source.put("path", "src/OrderService.java");
    source.put("originalId", "method:fixture");
    source.putArray("entryRefs").add("E1");
    String jsonl =
        new String(json.encodeCanonical(source).copyToByteArray(), StandardCharsets.UTF_8) + "\n";
    return bytes(jsonl);
  }

  private ImmutableBytes review() {
    ObjectNode root = mapper.createObjectNode();
    root.put("schemaVersion", "ontology-review-v3");
    root.put("humanAcceptanceStatus", "NOT_REVIEWED");
    root.putArray("taskResults");
    root.putArray("unresolved");
    root.putArray("assemblyIssues");
    return json.encodeCanonical(root);
  }

  private static ImmutableBytes bytes(String text) {
    return ImmutableBytes.copyOf(text.getBytes(StandardCharsets.UTF_8));
  }
}
