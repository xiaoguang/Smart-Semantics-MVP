package org.sourceanalysis.app.analysis.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.runtime.modeljob.ModelJobProviderBinding;

/** Direct reversible-reference contracts for one closed process-reading packet. */
class ProcessLocalReferenceMapTest {

  private static final CanonicalJsonCodec JSON = new CanonicalJsonCodec();
  private static final ModelRuntimeIdentityV1 IDENTITY =
      new ModelRuntimeIdentityV1("scripted", "fixture-model", "high", "read-only");

  @Test
  void stableActivityAndStatementOrdinalsRoundTripWithoutChangingTextOrArrayOrder() {
    List<String> activityIds = activityIds();
    List<String> statementRefs = activityIds.stream().map(id -> id + "/businessRules/0").toList();
    List<String> sourceRefs =
        activityIds.stream().map(id -> id.replace("activity", "source")).toList();
    ObjectNode globalInput = globalInput(activityIds, statementRefs, sourceRefs);
    ProcessLocalReferenceMap references = ProcessLocalReferenceMap.fromGlobalInput(globalInput);

    assertThat(references.activityRefs())
        .containsEntry(activityIds.get(0), "A1")
        .containsEntry(activityIds.get(9), "A10")
        .containsEntry(activityIds.get(10), "A11");
    assertThat(references.statementRefs())
        .containsEntry(statementRefs.get(0), "T1")
        .containsEntry(statementRefs.get(9), "T10");
    assertThat(references.sourceRefs())
        .containsEntry(sourceRefs.get(0), "S1")
        .containsEntry(sourceRefs.get(9), "S10");

    ObjectNode localInput = references.encodeInput(globalInput);
    JsonNode packet = localInput.path("readingPacket");
    assertThat(packet.path("schemaVersion").asText()).isEqualTo("process-reading-packet-v2");
    assertThat(packet.path("candidate").path("activityUses").get(0).path("activityId").asText())
        .isEqualTo("A11");
    assertThat(packet.path("candidate").path("activityUses").get(1).path("activityId").asText())
        .isEqualTo("A1");
    assertThat(packet.path("candidate").path("contextActivityIds"))
        .extracting(JsonNode::asText)
        .containsExactly("A2", "A3");
    assertThat(packet.path("reviewedActivities").get(0).path("activityId").asText())
        .isEqualTo("A11");
    assertThat(packet.path("reviewedActivities").get(0).path("businessPurpose").asText())
        .isEqualTo("正文里的A1/T1/S1和activity:pkg-k/Controller#save必须保持字面原样");
    assertThat(packet.path("reviewedActivities").get(0).path("businessRules"))
        .extracting(JsonNode::asText)
        .containsExactly("先核对A1", "再保留T10字样", "最后处理S1字样");

    assertThat(packet.path("statementDirectory").get(0))
        .isEqualTo(statementRow("T1", "A1", "businessRules/0"));
    assertThat(packet.path("statementDirectory").get(9))
        .isEqualTo(statementRow("T10", "A10", "businessRules/0"));
    assertThat(packet.path("sourceExcerpts").get(9).path("ref").asText()).isEqualTo("S10");
    assertThat(globalInput.path("readingPacket").path("schemaVersion").asText())
        .isEqualTo("process-reading-packet-v1");
    assertThat(
            globalInput
                .path("readingPacket")
                .path("candidate")
                .path("activityUses")
                .get(0)
                .path("activityId")
                .asText())
        .isEqualTo(activityIds.get(10));

    ObjectNode localProcess = localProcess();
    ObjectNode encodedProcess = localProcess.deepCopy();
    replaceLocalReference(encodedProcess, 0, "statementRefs", "T10");
    replaceLocalReference(encodedProcess, 0, "sourceRefs", "S10");
    replaceLocalReference(encodedProcess, 1, "statementRefs", "T1");
    replaceLocalReference(encodedProcess, 1, "sourceRefs", "S1");
    ObjectNode decoded = references.decodeProcess(encodedProcess);
    assertThat(decoded.path("activityUses").get(0).path("activityId").asText())
        .isEqualTo(activityIds.get(10));
    assertThat(decoded.path("activityUses").get(0).path("statementRefs").get(0).asText())
        .isEqualTo(statementRefs.get(9));
    assertThat(decoded.path("activityUses").get(0).path("sourceRefs").get(0).asText())
        .isEqualTo(sourceRefs.get(9));
    assertThat(decoded.path("activityUses").get(1).path("activityId").asText())
        .isEqualTo(activityIds.get(0));
    assertThat(decoded.path("businessRules")).isEqualTo(encodedProcess.path("businessRules"));
    assertThat(encodedProcess.path("businessRules").get(0).asText())
        .isEqualTo("T1 is literal prose");
  }

  @Test
  void equalLocalIdentifiersDoNotEraseRealCrossPacketIdentityFromFingerprint() throws Exception {
    ObjectNode firstGlobal =
        globalInput(
            List.of("activity:com.alpha/CustomerController#save"),
            List.of("activity:com.alpha/CustomerController#save/businessRules/0"),
            List.of("source:com.alpha/CustomerController#save"));
    ObjectNode secondGlobal =
        globalInput(
            List.of("activity:com.beta/CustomerController#save"),
            List.of("activity:com.beta/CustomerController#save/businessRules/0"),
            List.of("source:com.beta/CustomerController#save"));
    ProcessLocalReferenceMap firstMap = ProcessLocalReferenceMap.fromGlobalInput(firstGlobal);
    ProcessLocalReferenceMap secondMap = ProcessLocalReferenceMap.fromGlobalInput(secondGlobal);
    ObjectNode firstInput = firstMap.encodeInput(firstGlobal);
    ObjectNode secondInput = secondMap.encodeInput(secondGlobal);

    assertThat(firstMap.activityRefs())
        .containsEntry("activity:com.alpha/CustomerController#save", "A1");
    assertThat(secondMap.activityRefs())
        .containsEntry("activity:com.beta/CustomerController#save", "A1");
    assertThat(firstInput).isEqualTo(secondInput);
    assertThat(firstMap.toPrivateRecord()).isNotEqualTo(secondMap.toPrivateRecord());

    ObjectNode schema = JsonNodeFactory.instance.objectNode().put("type", "object");
    ProcessDiscoveryProfile profile =
        new ProcessDiscoveryProfile(8, 4, 8, 8_000, 64_000, 16_000, 4, 32, 2_000);
    String firstFingerprint =
        invokeProcessFingerprint(firstInput, firstMap, schema, schema, profile, binding());
    String secondFingerprint =
        invokeProcessFingerprint(secondInput, secondMap, schema, schema, profile, binding());
    assertThat(firstFingerprint)
        .as("the private real-identity map is semantic input even when every wire ID is equal")
        .isNotEqualTo(secondFingerprint);
  }

  @Test
  void selectedSourceEvidenceDoesNotRequireDroppingUnselectedActivitySourceReferences() {
    ObjectNode input =
        globalInput(
            List.of("activity:loan/LoanController#borrow"),
            List.of("activity:loan/LoanController#borrow/businessRules/0"),
            List.of("M1"));
    ObjectNode activity =
        (ObjectNode) input.path("readingPacket").path("reviewedActivities").get(0);
    activity.putArray("sourceRefs").add("M1").add("M2");

    ProcessLocalReferenceMap references = ProcessLocalReferenceMap.fromGlobalInput(input);
    ObjectNode encoded = references.encodeInput(input);
    JsonNode packet = encoded.path("readingPacket");

    assertThat(packet.path("reviewedActivities").get(0).path("sourceRefs")).hasSize(2);
    assertThat(packet.path("sourceExcerpts")).hasSize(1);
    assertThat(packet.path("sourceExcerpts").get(0).path("ref").asText()).isEqualTo("S1");
  }

  @Test
  void malformedStructuredReferencesAreRejectedInsteadOfPassedThrough() {
    ObjectNode input =
        globalInput(
            List.of("activity:loan/LoanController#borrow"),
            List.of("activity:loan/LoanController#borrow/businessRules/0"),
            List.of("M1"));
    ProcessLocalReferenceMap references = ProcessLocalReferenceMap.fromGlobalInput(input);
    ObjectNode process = localProcess();
    ((ObjectNode) process.withArray("activityUses").get(0)).put("activityId", "A10");

    assertThatThrownBy(() -> references.decodeProcess(process))
        .hasMessage("PROCESS_LOCAL_REFERENCE_INVALID");
  }

  private static String invokeProcessFingerprint(
      ObjectNode input,
      ProcessLocalReferenceMap localReferences,
      ObjectNode processSchema,
      ObjectNode finalSchema,
      ProcessDiscoveryProfile profile,
      ModelJobProviderBinding binding)
      throws Exception {
    Method method =
        DefaultBusinessProcessDiscovery.class.getDeclaredMethod(
            "processFingerprint",
            ObjectNode.class,
            ProcessLocalReferenceMap.class,
            ObjectNode.class,
            ObjectNode.class,
            ProcessDiscoveryProfile.class,
            ModelJobProviderBinding.class);
    method.setAccessible(true);
    try {
      return (String)
          method.invoke(
              new DefaultBusinessProcessDiscovery(zeroProvider()),
              input,
              localReferences,
              processSchema,
              finalSchema,
              profile,
              binding);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause();
      if (cause instanceof RuntimeException runtime) throw runtime;
      throw new AssertionError(cause);
    }
  }

  private static ObjectNode globalInput(
      List<String> activityIds, List<String> statementRefs, List<String> sourceRefs) {
    ObjectNode input = JsonNodeFactory.instance.objectNode();
    ObjectNode candidate = input.putObject("candidate");
    ArrayNode uses = candidate.putArray("activityUses");
    if (activityIds.size() == 1) {
      uses.addObject().put("activityId", activityIds.get(0));
    } else {
      uses.addObject().put("activityId", activityIds.get(activityIds.size() - 1));
      uses.addObject().put("activityId", activityIds.get(0));
      candidate.putArray("contextActivityIds").add(activityIds.get(1)).add(activityIds.get(2));
    }
    ObjectNode packet = input.putObject("readingPacket");
    packet.put("schemaVersion", "process-reading-packet-v1");
    packet.set("candidate", candidate.deepCopy());
    ArrayNode reviewedActivities = packet.putArray("reviewedActivities");
    List<String> presentedActivityIds = new ArrayList<>(activityIds);
    Collections.reverse(presentedActivityIds);
    for (String activityId : presentedActivityIds) {
      ObjectNode activity = reviewedActivities.addObject();
      activity.put("activityId", activityId);
      activity.put("name", "同名流程活动");
      activity.put("businessPurpose", "正文里的A1/T1/S1和activity:pkg-k/Controller#save必须保持字面原样");
      activity.putArray("businessRules").add("先核对A1").add("再保留T10字样").add("最后处理S1字样");
      activity.putArray("sourceRefs").add(sourceRefs.get(0));
    }
    ArrayNode directory = packet.putArray("statementDirectory");
    for (String statementRef : statementRefs) directory.add(statementRef);
    ArrayNode excerpts = packet.putArray("sourceExcerpts");
    for (String sourceRef : sourceRefs) {
      excerpts.addObject().put("ref", sourceRef).put("snippet", "冻结原文 A1/T1 字面串");
    }
    return input;
  }

  private static List<String> activityIds() {
    return List.of(
        "activity:pkg-a/Controller#save",
        "activity:pkg-b/Controller#save",
        "activity:pkg-c/Controller#save",
        "activity:pkg-d/Controller#save",
        "activity:pkg-e/Controller#save",
        "activity:pkg-f/Controller#save",
        "activity:pkg-g/Controller#save",
        "activity:pkg-h/Controller#save",
        "activity:pkg-i/Controller#save",
        "activity:pkg-j/Controller#save",
        "activity:pkg-k/Controller#save");
  }

  private static ObjectNode statementRow(String localRef, String activityId, String fieldPath) {
    ObjectNode row = JsonNodeFactory.instance.objectNode();
    row.put("statementRef", localRef);
    row.put("activityId", activityId);
    row.put("fieldPath", fieldPath);
    return row;
  }

  private static ObjectNode localProcess() {
    ObjectNode process = JsonNodeFactory.instance.objectNode();
    ArrayNode uses = process.putArray("activityUses");
    ObjectNode firstUse = uses.addObject();
    firstUse.put("activityId", "A11");
    firstUse.putArray("statementRefs").add("T1");
    firstUse.putArray("sourceRefs").add("S1");
    ObjectNode secondUse = uses.addObject();
    secondUse.put("activityId", "A1");
    secondUse.putArray("statementRefs").add("T10");
    secondUse.putArray("sourceRefs").add("S10");
    process.putArray("businessRules").add("T1 is literal prose").add("A1/T10 remain text");
    return process;
  }

  private static void replaceLocalReference(
      ObjectNode process, int useIndex, String field, String localReference) {
    ObjectNode use = (ObjectNode) process.withArray("activityUses").get(useIndex);
    use.withArray(field).set(0, JsonNodeFactory.instance.textNode(localReference));
  }

  private static ModelJobProviderBinding binding() {
    return new ModelJobProviderBinding(
        "scripted", "fingerprint-account", 1, zeroProvider(), IDENTITY);
  }

  private static StructuredModelProvider zeroProvider() {
    return request -> {
      throw new AssertionError("local-reference contract tests must not invoke a model");
    };
  }
}
