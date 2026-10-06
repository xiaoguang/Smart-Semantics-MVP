package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.material.publish.EntryEvidenceReader;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitHandle;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Direct contracts for the additive, reversible formal reading-packet v7 projection. */
final class OntologyLeanLinkPacketV7ContractsTest {
  private static final String ENTRY = "entry:" + "a".repeat(64);
  private static final String CALLER = "method:caller";
  private static final String FIRST_TARGET = "method:first-target";
  private static final String SECOND_TARGET = "method:second-target";
  private static final String FIRST_CALL = "call:first";
  private static final String SECOND_CALL = "call:second";
  private static final String FAILED_CALL = "call:failed";
  private static final String BODY =
      "public void save(String firstId, String secondId) {\n"
          + "  lookup(firstId);\n"
          + "  lookup(secondId);\n"
          + "  sendToRemoteSystem();\n"
          + "}\n";

  private final ObjectMapper mapper = new ObjectMapper();
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  @Test
  void completeMethodTextAndParametersStayVisibleWhileDuplicateAstMetadataStaysPrivate() {
    Fixture fixture = fixture();
    OntologyReadingPacket packet = packet(fixture, List.of(fixture.caller()));
    JsonNode model = json.parseCanonical(packet.modelInput());
    JsonNode method = modelUnit(model, localRef(packet, fixture.caller()));

    assertThat(method.path("sourceText").asText()).isEqualTo(BODY);
    assertThat(method.path("parameters").get(0).path("name").asText()).isEqualTo("firstId");
    assertThat(method.has("signature")).isFalse();
    assertThat(method.has("returnTypeText")).isFalse();
    assertThat(method.has("annotations")).isFalse();
    assertThat(method.has("controls")).isFalse();
    assertThat(method.has("exits")).isFalse();
    assertThat(model.toString())
        .doesNotContain("AST_ONLY_ANNOTATION_V7", "AST_ONLY_CONTROL_V7", "AST_ONLY_EXIT_V7");

    JsonNode privateMethod = packet.resolve(localRef(packet, fixture.caller())).content();
    assertThat(privateMethod.path("annotations").get(0).asText())
        .isEqualTo("AST_ONLY_ANNOTATION_V7");
    assertThat(privateMethod.path("controls").get(0).path("text").asText())
        .isEqualTo("AST_ONLY_CONTROL_V7");
  }

  @Test
  void selectedCallUsesDecodeInPhysicalOrderWithTheirArgumentsAndTargetMappings() {
    Fixture fixture = fixture();
    OntologyReadingPacket packet =
        packet(
            fixture,
            List.of(
                fixture.secondCall(),
                fixture.secondTarget(),
                fixture.caller(),
                fixture.firstTarget(),
                fixture.firstCall()));
    JsonNode model = json.parseCanonical(packet.modelInput());

    ArrayNode decoded = OntologyModelProjection.decodeCallRows(model);

    assertThat(decoded).hasSize(2);
    assertThat(decoded.get(0).path("site").path("startOffsetUtf16").asInt()).isEqualTo(54);
    assertThat(decoded.get(0).path("actualArguments").get(0).path("expression").asText())
        .isEqualTo("firstId");
    assertThat(decoded.get(0).path("callRef").asText())
        .isEqualTo(localRef(packet, fixture.firstCall()));
    assertThat(decoded.get(0).path("selectedTargets").get(0).path("targetRef").asText())
        .isEqualTo(localRef(packet, fixture.firstTarget()));

    assertThat(decoded.get(1).path("site").path("startOffsetUtf16").asInt()).isEqualTo(73);
    assertThat(decoded.get(1).path("actualArguments").get(0).path("expression").asText())
        .isEqualTo("secondId");
    assertThat(decoded.get(1).path("callRef").asText())
        .isEqualTo(localRef(packet, fixture.secondCall()));
    assertThat(decoded.get(1).path("selectedTargets").get(0).path("targetRef").asText())
        .isEqualTo(localRef(packet, fixture.secondTarget()));
    assertThat(textValues(model.path("callUses"), "ordinal")).containsExactly("0", "1");
  }

  @Test
  void unselectedQueryFailureKeepsItsExactPositionAndObservationWithoutSendingCallDetails() {
    Fixture fixture = fixture();
    OntologyReadingPacket packet = packet(fixture, List.of(fixture.caller()));
    JsonNode model = json.parseCanonical(packet.modelInput());
    JsonNode limitation =
        stream(model.path("limitationRows"))
            .filter(row -> "QUERY_FAILED".equals(row.path("resolution").asText()))
            .findFirst()
            .orElseThrow();

    assertThat(model.path("limitationRows")).hasSize(1);

    assertThat(limitation.path("entryRef").asText()).isEqualTo("E1");
    assertThat(limitation.path("fromRef").asText()).isEqualTo(localRef(packet, fixture.caller()));
    assertThat(limitation.path("site")).isEqualTo(mapper.valueToTree(List.of(93, 20, 13, 13)));
    assertThat(limitation.path("detail").asText()).isEqualTo("QUERY_FAILURE_DETAIL_V7");
    assertThat(limitation.path("observationRefs")).hasSize(1);
    String observationRef = limitation.path("observationRefs").get(0).asText();
    assertThat(model.path("callEvidence").path("observations").path(observationRef).path("detail"))
        .isEqualTo(mapper.getNodeFactory().textNode("QUERY_FAILURE_OBSERVATION_V7"));
    assertThat(model.path("callRows")).isEmpty();
    assertThat(model.path("unreadSummary").path("selectedCallUses").asInt(-1)).isZero();
    assertThat(model.path("unreadSummary").path("privateUnselectedCallUses").asInt(-1))
        .isEqualTo(3);
    assertThat(model.path("unreadSummary").path("privateDirectory").asText())
        .isEqualTo("formalCallSites");
    assertThat(model.toString())
        .doesNotContain("PRIVATE_QUERY_ARGUMENT_V7", "PRIVATE_QUERY_TARGET_V7");
    assertThat(new String(packet.canonicalInput().copyToByteArray(), StandardCharsets.UTF_8))
        .contains("PRIVATE_QUERY_ARGUMENT_V7", "PRIVATE_QUERY_TARGET_V7");
  }

  @Test
  void formalV7RequiresTheVersionTwoBundleDecisionWithoutChangingFormalV6() {
    Fixture fixture = fixture();
    ObjectNode versionOne = decision("link-bundle-rule-v1");
    ObjectNode versionTwo = decision("link-bundle-rule-v2");

    assertThat(
            OntologyReadingPacket.formalV6(
                    fixture.corpus(), List.of(fixture.caller()), 10_000, versionOne)
                .modelProjectionVersion())
        .isEqualTo("ontology-model-reading-v6");
    assertThat(
            OntologyReadingPacket.formalV7(
                    fixture.corpus(), List.of(fixture.caller()), 10_000, versionTwo)
                .modelProjectionVersion())
        .isEqualTo("ontology-model-reading-v7");
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                OntologyReadingPacket.formalV7(
                    fixture.corpus(), List.of(fixture.caller()), 10_000, versionOne))
        .hasMessage("ONTOLOGY_LINK_BUNDLE_DECISION_INVALID");
  }

  private OntologyReadingPacket packet(Fixture fixture, List<UnitHandle> selected) {
    return OntologyReadingPacket.formalV7(
        fixture.corpus(), selected, 10_000, decision("link-bundle-rule-v2"));
  }

  private ObjectNode decision(String ruleVersion) {
    ObjectNode value = mapper.createObjectNode();
    value.put("ruleVersion", ruleVersion);
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

  private JsonNode modelUnit(JsonNode model, String ref) {
    for (JsonNode unit : model.path("units")) {
      if (ref.equals(unit.path("ref").asText())) return unit.path("content");
    }
    throw new AssertionError("The packet must contain the selected source unit " + ref);
  }

  private String localRef(OntologyReadingPacket packet, UnitHandle handle) {
    return packet.units().stream()
        .filter(
            unit ->
                unit.kind() == handle.kind()
                    && unit.originalId().equals(handle.originalId())
                    && unit.entryUses().contains(handle.entryId()))
        .map(OntologyReadingPacket.PackedUnit::localRef)
        .findFirst()
        .orElseThrow(() -> new AssertionError("The selected unit must have a packet reference"));
  }

  private List<String> textValues(JsonNode rows, String field) {
    java.util.ArrayList<String> values = new java.util.ArrayList<>();
    rows.forEach(row -> values.add(row.path(field).asText()));
    return List.copyOf(values);
  }

  private java.util.stream.Stream<JsonNode> stream(JsonNode values) {
    java.util.ArrayList<JsonNode> copy = new java.util.ArrayList<>();
    values.forEach(copy::add);
    return copy.stream();
  }

  private Fixture fixture() {
    ObjectNode header = mapper.createObjectNode();
    header.putObject("header").putObject("sourceBasis").put("kind", "PREPARED_V1");
    ObjectNode entry = mapper.createObjectNode();
    entry.put("entryId", ENTRY);
    entry.putObject("sourceBasis").put("kind", "PREPARED_V1");
    entry.put("assemblyStatus", "ASSEMBLED");
    entry.putObject("entry").put("method", "POST").put("route", "/v7/save");

    ObjectNode java = entry.putObject("java");
    ArrayNode methods = java.putArray("methods");
    methods.add(method(CALLER, "save", BODY, 10));
    methods.add(method(FIRST_TARGET, "firstTarget", "void firstTarget(String id) {}", 50));
    methods.add(method(SECOND_TARGET, "secondTarget", "void secondTarget(String id) {}", 60));
    ArrayNode calls = java.putArray("calls");
    calls.add(locatedCall(FIRST_CALL, FIRST_TARGET, "firstTarget", "firstId", 54, 11));
    calls.add(locatedCall(SECOND_CALL, SECOND_TARGET, "secondTarget", "secondId", 73, 12));
    calls.add(failedCall());
    java.putArray("observations");
    java.putArray("supportingSources");
    entry.putObject("frontend").putArray("units");
    ObjectNode persistence = entry.putObject("persistence");
    persistence.putArray("bindings");
    persistence.putArray("statements");
    persistence.putArray("resources");
    persistence.putArray("sqlAnalyses");
    entry.putArray("limitations");
    entry.putArray("sourceRefs");

    OntologyEvidenceCorpus corpus =
        OntologyEvidenceCorpus.fromVerifiedDirectory(
            new EntryEvidenceReader.Directory(
                json.encodeCanonical(header),
                ImmutableBytes.copyOf(new byte[0]),
                List.of(
                    new EntryEvidenceReader.EntryDocument(ENTRY, json.encodeCanonical(entry)))));
    return new Fixture(
        corpus,
        new UnitHandle(ENTRY, UnitKind.JAVA_METHOD, CALLER),
        new UnitHandle(ENTRY, UnitKind.JAVA_METHOD, FIRST_TARGET),
        new UnitHandle(ENTRY, UnitKind.JAVA_METHOD, SECOND_TARGET),
        new UnitHandle(ENTRY, UnitKind.JAVA_CALL, FIRST_CALL),
        new UnitHandle(ENTRY, UnitKind.JAVA_CALL, SECOND_CALL));
  }

  private ObjectNode method(String key, String name, String sourceText, int line) {
    ObjectNode method = mapper.createObjectNode();
    method.put("methodKey", key);
    method.put("name", name);
    method.put("declaringType", "example.V7Handler");
    method.put("signature", "AST_ONLY_SIGNATURE_V7");
    method.put("returnTypeText", "AST_ONLY_RETURN_TYPE_V7");
    method.putArray("parameters").addObject().put("ordinal", 0).put("name", "firstId");
    method.putArray("modifiers").add("public");
    method.putArray("annotations").add("AST_ONLY_ANNOTATION_V7");
    method
        .putArray("controls")
        .addObject()
        .put("kind", "IF")
        .put("text", "AST_ONLY_CONTROL_V7")
        .put("startLine", line);
    method.putArray("exits").addObject().put("kind", "RETURN").put("text", "AST_ONLY_EXIT_V7");
    method.putNull("enclosingMethodKey");
    ObjectNode source = method.putObject("source");
    source.put("path", "src/V7Handler.java");
    source.put("startLine", line);
    source.put("endLine", line + Math.max(0, sourceText.split("\\n").length - 1));
    source.put("startOffsetUtf16", 100);
    source.put("lengthUtf16", sourceText.length());
    source.put("text", sourceText);
    return method;
  }

  private ObjectNode locatedCall(
      String callKey, String targetKey, String targetName, String argument, int offset, int line) {
    ObjectNode call = baseCall(callKey, "lookup(" + argument + ")", offset, line);
    call.put("resolution", "LOCATED");
    call.putArray("actualArguments").addObject().put("ordinal", 0).put("expression", argument);
    ObjectNode target = call.putArray("targets").addObject();
    target.put("methodKey", targetKey);
    target.put("displayName", targetName);
    target.putArray("roles").add("DECLARATION");
    target.putArray("navigationKinds").add("DEFINITION");
    target.put("expansion", "BODY_INCLUDED");
    ObjectNode association = target.putArray("argumentAssociations").addObject();
    association.putArray("actualOrdinals").add(0);
    association.put("formalOrdinal", 0);
    association.put("kind", "POSITIONAL");
    call.putArray("observations");
    return call;
  }

  private ObjectNode failedCall() {
    ObjectNode call = baseCall(FAILED_CALL, "sendToRemoteSystem()", 93, 13);
    call.put("resolution", "QUERY_FAILED");
    call.put("resolutionDetail", "QUERY_FAILURE_DETAIL_V7");
    call.putArray("actualArguments")
        .addObject()
        .put("ordinal", 0)
        .put("expression", "PRIVATE_QUERY_ARGUMENT_V7");
    ObjectNode target = call.putArray("targets").addObject();
    target.putNull("methodKey");
    target.put("displayName", "PRIVATE_QUERY_TARGET_V7");
    target.putArray("roles").add("DECLARATION");
    target.putArray("navigationKinds").add("CALL_HIERARCHY");
    target.put("expansion", "NOT_EXPANDED");
    target.put("reason", "The unselected target detail must stay private.");
    target.putArray("argumentAssociations");
    ObjectNode observation = call.putArray("observations").addObject();
    observation.put("code", "QUERY_FAILED");
    observation.put("operation", "JDT_LANGUAGE_SERVER");
    observation.put("uriKind", "SOURCE");
    observation.put("sourceRange", call.path("site").deepCopy());
    observation.put("association", "FAILED");
    observation.put("detail", "QUERY_FAILURE_OBSERVATION_V7");
    observation.put("declarationKey", "remote-system-call");
    observation.put("declaringTypeKey", "example.RemoteSystem");
    observation.put("displayIdentity", "sendToRemoteSystem");
    observation.put("typeOrigin", "EXTERNAL");
    return call;
  }

  private ObjectNode baseCall(String callKey, String expression, int offset, int line) {
    ObjectNode call = mapper.createObjectNode();
    call.put("callKey", callKey);
    call.put("callerMethodKey", CALLER);
    call.put("kind", "METHOD");
    ObjectNode site = call.putObject("site");
    site.put("startOffsetUtf16", offset);
    site.put("lengthUtf16", expression.length());
    site.put("startLine", line);
    site.put("endLine", line);
    call.set("navigationSite", site.deepCopy());
    call.put("expression", expression);
    call.putNull("receiverExpression");
    call.putArray("enclosingControlIndexes");
    call.put("deferred", false);
    return call;
  }

  private record Fixture(
      OntologyEvidenceCorpus corpus,
      UnitHandle caller,
      UnitHandle firstTarget,
      UnitHandle secondTarget,
      UnitHandle firstCall,
      UnitHandle secondCall) {}
}
