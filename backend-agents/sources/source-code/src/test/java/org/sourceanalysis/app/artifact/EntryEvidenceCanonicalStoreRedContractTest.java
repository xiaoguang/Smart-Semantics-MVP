package org.sourceanalysis.app.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.material.publish.CodeReadingMaterialReader;
import org.sourceanalysis.app.analysis.material.publish.EntryEvidencePublisher;
import org.sourceanalysis.app.analysis.material.publish.EntryEvidenceReader;

/** RED contracts for the variable, self-contained Step 05 entry-evidence publication. */
class EntryEvidenceCanonicalStoreRedContractTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final AnalysisRunId RUN_ID = AnalysisRunId.parse("analysis-run:" + "a".repeat(64));

  @TempDir Path temporaryDirectory;

  @Test
  void installsAndReopensSixtyFiveFlatEntryFilesWithExactIndexAndCoverageSet() throws Exception {
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = entryEvidencePolicies(json);
    List<String> entryIds = entryIds(65);
    List<CanonicalModulePayload> payloads = entryEvidencePayloads(json, policies, entryIds);
    ArtifactControls controls = controls(policies);

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
      CanonicalModuleArtifactStore modules = moduleStore(handle, json, policies, 67);
      CanonicalAnalysisStepArtifactStore steps = stepStore(handle, json, policies, 67);
      ModuleInstallRequest moduleRequest =
          request(
              new AnalysisStepModuleAddress(
                  RUN_ID, AnalysisStepKey.BUSINESS_FLOWS, 4, "entry-evidence"),
              controls,
              payloads);

      InstalledModulePublication module = modules.install(moduleRequest);
      assertThat(module.artifactDescriptors())
          .extracting(ArtifactDescriptor::fileName)
          .containsExactlyElementsOf(
              payloads.stream().map(CanonicalModulePayload::fileName).toList());

      ReopenedModulePublication reopened = modules.reopen(module.reference());
      assertThat(reopened.payloads()).hasSize(67);
      assertThat(reopened.payloads())
          .filteredOn(
              payload -> payload.descriptor().fileName().matches("entry-[0-9a-f]{64}\\.json"))
          .allSatisfy(
              payload -> {
                String entryId = payloadEntryId(payload);
                assertThat(payload.descriptor().fileName())
                    .isEqualTo("entry-" + entryId.substring("entry:".length()) + ".json");
              });
      assertThat(
              JSON.readTree(reopened.payloads().get(2).canonicalUtf8().copyToByteArray())
                  .path("java")
                  .path("supportingSources"))
          .isEqualTo(
              JSON.readTree(payloads.get(2).canonicalUtf8().copyToByteArray())
                  .path("java")
                  .path("supportingSources"));
      assertThat(
              JSON.readTree(reopened.payloads().get(2).canonicalUtf8().copyToByteArray())
                  .path("java")
                  .path("technicalEnhancements"))
          .isEqualTo(
              JSON.readTree(payloads.get(2).canonicalUtf8().copyToByteArray())
                  .path("java")
                  .path("technicalEnhancements"));
      JsonNode failedEntry =
          JSON.readTree(reopened.payloads().get(64).canonicalUtf8().copyToByteArray());
      assertThat(failedEntry.path("entryId").asText()).isEqualTo(entryIds.get(64));
      assertThat(failedEntry.path("assemblyStatus").asText()).isEqualTo("NOT_ASSEMBLED");
      assertThat(failedEntry.path("limitations").findValuesAsText("code"))
          .containsExactly("JDT_QUERY_FAILED");
      String coverage =
          new String(
              reopened.payloads().get(66).canonicalUtf8().copyToByteArray(),
              StandardCharsets.UTF_8);
      assertThat(coverage)
          .contains(
              "request:unmatched",
              "/orders/unmatched",
              "getQueryParams",
              "request:ambiguous",
              "candidateEntryIds");
      JsonNode firstEntry =
          JSON.readTree(reopened.payloads().get(0).canonicalUtf8().copyToByteArray());
      assertThat(firstEntry.path("java").path("methods").get(0).path("body").asText())
          .contains("public void loadOrders");
      assertThat(firstEntry.path("java").path("supportingSources").get(0).path("content").asText())
          .contains("Support");
      assertThat(firstEntry.path("persistence").path("resources").get(0).path("content").asText())
          .contains("<select id=\"list\">");
      assertThat(
              firstEntry
                  .path("persistence")
                  .path("sqlAnalyses")
                  .get(0)
                  .path("orderBy")
                  .get(0)
                  .path("direction")
                  .asText())
          .isEqualTo("DESC");

      AnalysisStepInstallRequest stepRequest =
          new AnalysisStepInstallRequest(
              new AnalysisStepPublicationAddress(RUN_ID, AnalysisStepKey.BUSINESS_FLOWS),
              new AnalysisStepPublisherModuleProvenance(module.reference()),
              List.of(
                  upstreamReference(AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, '1'),
                  upstreamReference(AnalysisStepKey.APPLICATION_DISCOVERY, '2'),
                  upstreamReference(AnalysisStepKey.PROGRAM_GRAPHS, '3'),
                  upstreamReference(AnalysisStepKey.PROVEN_CODE_FACTS, '4')),
              controls,
              ModuleCompletionStatus.SUCCEEDED,
              List.of(),
              payloads.stream()
                  .map(EntryEvidenceCanonicalStoreRedContractTest::stepPayload)
                  .toList(),
              null);
      InstalledAnalysisStepPublication step = steps.install(stepRequest);
      assertThat(steps.reopen(step.reference()).semanticPayloads()).hasSize(67);
      EntryEvidenceReader reader = new EntryEvidenceReader(modules, steps);
      EntryEvidenceReader.EntryDocument first = reader.read(step.reference(), entryIds.get(0));
      assertThat(first.entryId()).isEqualTo(entryIds.get(0));
      assertThat(new String(first.canonicalJson().copyToByteArray(), StandardCharsets.UTF_8))
          .contains("method:shared", "supportingSources", "technicalEnhancements");
      assertThatThrownBy(() -> reader.read(step.reference(), entryIds.get(0) + "/../other"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("ENTRY_EVIDENCE_READER_INVALID");
      assertThatThrownBy(() -> reader.read(step.reference(), "entry:" + "f".repeat(64)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("ENTRY_EVIDENCE_READER_INVALID");
      // The historical Packet reader must reject this new material before any Step07 model work.
      assertThatThrownBy(() -> new CodeReadingMaterialReader(steps).reopen(step.reference()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("CODE_READING_MATERIAL_SET_INVALID");
    }
  }

  @Test
  void reopensEntryEvidenceAfterMovingTheWholeRunStoreRoot() throws Exception {
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = entryEvidencePolicies(json);
    ArtifactControls controls = controls(policies);
    List<String> entryIds = entryIds(2);
    String entryId = entryIds.get(0);
    List<CanonicalModulePayload> payloads = entryEvidencePayloads(json, policies, entryIds);
    Path originalRoot = temporaryDirectory.resolve("store-before-relocation");
    Path relocatedRoot = temporaryDirectory.resolve("store-after-relocation");
    Files.createDirectory(originalRoot);

    AnalysisStepPublicationReference stepReference;
    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(originalRoot)) {
      CanonicalModuleArtifactStore modules = moduleStore(handle, json, policies, 5);
      CanonicalAnalysisStepArtifactStore steps = stepStore(handle, json, policies, 5);
      AnalysisStepModuleAddress moduleAddress =
          new AnalysisStepModuleAddress(
              RUN_ID, AnalysisStepKey.BUSINESS_FLOWS, 4, "entry-evidence");
      InstalledModulePublication module =
          modules.install(request(moduleAddress, controls, payloads));
      AnalysisStepInstallRequest stepRequest =
          new AnalysisStepInstallRequest(
              new AnalysisStepPublicationAddress(RUN_ID, AnalysisStepKey.BUSINESS_FLOWS),
              new AnalysisStepPublisherModuleProvenance(module.reference()),
              List.of(
                  upstreamReference(AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, '1'),
                  upstreamReference(AnalysisStepKey.APPLICATION_DISCOVERY, '2'),
                  upstreamReference(AnalysisStepKey.PROGRAM_GRAPHS, '3'),
                  upstreamReference(AnalysisStepKey.PROVEN_CODE_FACTS, '4')),
              controls,
              ModuleCompletionStatus.SUCCEEDED,
              List.of(),
              payloads.stream()
                  .map(EntryEvidenceCanonicalStoreRedContractTest::stepPayload)
                  .toList(),
              null);
      stepReference = steps.install(stepRequest).reference();
    }

    Files.move(originalRoot, relocatedRoot);

    try (RunStoreHandle movedHandle = RunStoreBootstrap.open(relocatedRoot)) {
      EntryEvidenceReader reader =
          new EntryEvidenceReader(
              moduleStore(movedHandle, json, policies, 5),
              stepStore(movedHandle, json, policies, 5));
      EntryEvidenceReader.Directory directory = reader.reopen(stepReference);
      EntryEvidenceReader.EntryDocument entry = reader.read(stepReference, entryId);

      assertThat(directory.entries())
          .extracting(EntryEvidenceReader.EntryDocument::entryId)
          .containsExactlyElementsOf(entryIds);
      assertThat(entry.canonicalJson().copyToByteArray())
          .asString(StandardCharsets.UTF_8)
          .contains(entryId, "loadOrders", "sourceRefs");
      assertThat(directory.frontendCoverageCanonicalJsonl().copyToByteArray())
          .asString(StandardCharsets.UTF_8)
          .contains("request:unmatched", "/orders/unmatched", "getQueryParams");
    }
  }

  @Test
  void rejectsMismatchedEntryIdentityExtraFilesAndPathEscapeWithoutInstallingAReceipt()
      throws Exception {
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = entryEvidencePolicies(json);
    ArtifactControls controls = controls(policies);
    List<String> entryIds = entryIds(2);

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
      CanonicalModuleArtifactStore modules = moduleStore(handle, json, policies, 8);
      List<CanonicalModulePayload> valid = entryEvidencePayloads(json, policies, entryIds);
      String extraEntryId = entryIds(3).get(2);
      List<CanonicalModulePayload> extra =
          List.of(
              valid.get(0),
              valid.get(1),
              entryPayload(json, policies, extraEntryId, extraEntryId),
              valid.get(2),
              valid.get(3));
      assertThatThrownBy(
              () ->
                  modules.install(
                      request(
                          new AnalysisStepModuleAddress(
                              RUN_ID, AnalysisStepKey.BUSINESS_FLOWS, 4, "entry-evidence"),
                          controls,
                          extra)))
          .isInstanceOf(ArtifactStoreException.class);

      CanonicalModulePayload mismatchedFirst =
          entryPayload(json, policies, entryIds.get(1), entryIds.get(0));
      CanonicalModulePayload mismatchedSecond =
          entryPayload(json, policies, entryIds.get(0), entryIds.get(1));
      assertThatThrownBy(
              () ->
                  modules.install(
                      request(
                          new AnalysisStepModuleAddress(
                              RUN_ID, AnalysisStepKey.BUSINESS_FLOWS, 4, "entry-evidence"),
                          controls,
                          List.of(
                              mismatchedFirst,
                              mismatchedSecond,
                              indexPayload(json, policies, entryIds),
                              coveragePayload(json, policies)))))
          .isInstanceOf(ArtifactStoreException.class);

      CanonicalModulePayload escaped =
          entryPayloadWithFileName(
              json, policies, entryIds.get(0), "frontend-coverage.jsonl/../entry-escape.json");
      assertThatThrownBy(
              () ->
                  modules.install(
                      request(
                          new AnalysisStepModuleAddress(
                              RUN_ID, AnalysisStepKey.BUSINESS_FLOWS, 4, "entry-evidence"),
                          controls,
                          List.of(
                              entryPayload(json, policies, entryIds.get(1), entryIds.get(1)),
                              indexPayload(json, policies, entryIds),
                              coveragePayload(json, policies),
                              escaped))))
          .isInstanceOf(ArtifactStoreException.class);
      assertThat(receiptFiles(temporaryDirectory)).isEmpty();
    }
  }

  @Test
  void rejectsCapacityFailureWithoutInstallingAFalseSuccessfulCollection() throws Exception {
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = entryEvidencePolicies(json);
    List<CanonicalModulePayload> payloads = entryEvidencePayloads(json, policies, entryIds(65));
    ArtifactControls controls = controls(policies);

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(
              handle, json, policies, new ArtifactStoreLimits(67, 512, 512, 128));
      assertThatThrownBy(
              () ->
                  modules.install(
                      request(
                          new AnalysisStepModuleAddress(
                              RUN_ID, AnalysisStepKey.BUSINESS_FLOWS, 4, "entry-evidence"),
                          controls,
                          payloads)))
          .isInstanceOf(ArtifactStoreException.class);
      assertThat(receiptFiles(temporaryDirectory)).isEmpty();
    }
  }

  @Test
  void installsAndReopensAnEmptyBackendDenominatorWithFrontendCoverageOnly() throws Exception {
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = entryEvidencePolicies(json);
    ArtifactControls controls = controls(policies);
    List<CanonicalModulePayload> payloads = entryEvidencePayloads(json, policies, List.of());

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
      CanonicalModuleArtifactStore modules = moduleStore(handle, json, policies, 4);
      CanonicalAnalysisStepArtifactStore steps = stepStore(handle, json, policies, 4);
      AnalysisStepModuleAddress address =
          new AnalysisStepModuleAddress(
              RUN_ID, AnalysisStepKey.BUSINESS_FLOWS, 4, "entry-evidence");
      InstalledModulePublication module = modules.install(request(address, controls, payloads));
      assertThat(module.artifactDescriptors())
          .extracting(ArtifactDescriptor::fileName)
          .containsExactly("entry-evidence-index.json", "frontend-coverage.jsonl");

      AnalysisStepInstallRequest stepRequest =
          new AnalysisStepInstallRequest(
              new AnalysisStepPublicationAddress(RUN_ID, AnalysisStepKey.BUSINESS_FLOWS),
              new AnalysisStepPublisherModuleProvenance(module.reference()),
              List.of(
                  upstreamReference(AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, '1'),
                  upstreamReference(AnalysisStepKey.APPLICATION_DISCOVERY, '2'),
                  upstreamReference(AnalysisStepKey.PROGRAM_GRAPHS, '3'),
                  upstreamReference(AnalysisStepKey.PROVEN_CODE_FACTS, '4')),
              controls,
              ModuleCompletionStatus.SUCCEEDED,
              List.of(),
              payloads.stream()
                  .map(EntryEvidenceCanonicalStoreRedContractTest::stepPayload)
                  .toList(),
              null);
      InstalledAnalysisStepPublication step = steps.install(stepRequest);
      assertThat(steps.reopen(step.reference()).semanticPayloads())
          .extracting(payload -> payload.descriptor().fileName())
          .containsExactly("entry-evidence-index.json", "frontend-coverage.jsonl");
      assertThat(
              JSON.readTree(
                      steps
                          .reopen(step.reference())
                          .semanticPayloads()
                          .get(0)
                          .canonicalUtf8()
                          .copyToByteArray())
                  .path("entries"))
          .isEmpty();
    }
  }

  @Test
  void installsAndReopensAnEntryWhoseHexIdentitySortsAfterTheIndexFile() throws Exception {
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = entryEvidencePolicies(json);
    ArtifactControls controls = controls(policies);
    String fEntryId = "entry:" + "f".repeat(64);
    List<CanonicalModulePayload> payloads =
        entryEvidencePayloads(json, policies, List.of(fEntryId)).stream()
            .sorted(Comparator.comparing(CanonicalModulePayload::fileName))
            .toList();

    try (RunStoreHandle handle = RunStoreBootstrap.openForTest(temporaryDirectory)) {
      CanonicalModuleArtifactStore modules = moduleStore(handle, json, policies, 4);
      CanonicalAnalysisStepArtifactStore steps = stepStore(handle, json, policies, 4);
      AnalysisStepModuleAddress address =
          new AnalysisStepModuleAddress(
              RUN_ID, AnalysisStepKey.BUSINESS_FLOWS, 4, "entry-evidence");
      InstalledModulePublication module = modules.install(request(address, controls, payloads));
      assertThat(module.artifactDescriptors())
          .extracting(ArtifactDescriptor::fileName)
          .containsExactly(
              "entry-evidence-index.json",
              "entry-" + "f".repeat(64) + ".json",
              "frontend-coverage.jsonl");

      AnalysisStepInstallRequest stepRequest =
          new AnalysisStepInstallRequest(
              new AnalysisStepPublicationAddress(RUN_ID, AnalysisStepKey.BUSINESS_FLOWS),
              new AnalysisStepPublisherModuleProvenance(module.reference()),
              List.of(
                  upstreamReference(AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, '1'),
                  upstreamReference(AnalysisStepKey.APPLICATION_DISCOVERY, '2'),
                  upstreamReference(AnalysisStepKey.PROGRAM_GRAPHS, '3'),
                  upstreamReference(AnalysisStepKey.PROVEN_CODE_FACTS, '4')),
              controls,
              ModuleCompletionStatus.SUCCEEDED,
              List.of(),
              payloads.stream()
                  .map(EntryEvidenceCanonicalStoreRedContractTest::stepPayload)
                  .toList(),
              null);
      InstalledAnalysisStepPublication step = steps.install(stepRequest);
      assertThat(steps.reopen(step.reference()).semanticPayloads())
          .extracting(payload -> payload.descriptor().fileName())
          .containsExactly(
              "entry-evidence-index.json",
              "entry-" + "f".repeat(64) + ".json",
              "frontend-coverage.jsonl");
    }
  }

  private static List<CanonicalModulePayload> entryEvidencePayloads(
      CanonicalJsonCodec json, CanonicalArtifactPolicyRegistry policies, List<String> entryIds) {
    List<CanonicalModulePayload> payloads = new ArrayList<>();
    for (int index = 0; index < entryIds.size(); index++) {
      payloads.add(entryPayload(json, policies, entryIds.get(index), entryIds.get(index)));
    }
    payloads.add(indexPayload(json, policies, entryIds));
    payloads.add(coveragePayload(json, policies));
    return payloads;
  }

  private static CanonicalModulePayload indexPayload(
      CanonicalJsonCodec json, CanonicalArtifactPolicyRegistry policies, List<String> entryIds) {
    ObjectNode body = document("entry-evidence-index-v1", "ENTRY_EVIDENCE_INDEX");
    body.set("header", lineageHeader());
    ArrayNode entries = body.putArray("entries");
    for (String entryId : entryIds) {
      ObjectNode entry = entries.addObject();
      entry.put("entryId", entryId);
      entry.put("file", fileName(entryId));
      entry.put("route", "/orders/list");
      entry.putObject("methodCondition").put("kind", "UNRESTRICTED").putArray("methods");
      entry.put("handlerFqn", "fixture.OrdersController");
      entry.put("methodKey", "method:shared");
      entry.put("assemblyStatus", entryId.endsWith("41") ? "NOT_ASSEMBLED" : "ASSEMBLED");
    }
    return standalonePayload(
        json,
        policies,
        "entry-evidence-index.json",
        "ENTRY_EVIDENCE_INDEX",
        "entry-evidence-index-v1",
        "entry-evidence-index",
        body);
  }

  private static CanonicalModulePayload coveragePayload(
      CanonicalJsonCodec json, CanonicalArtifactPolicyRegistry policies) {
    ObjectNode header = document("frontend-evidence-coverage-v1", "FRONTEND_EVIDENCE_COVERAGE");
    header.put("recordType", "HEADER").put("producer", EntryEvidencePublisher.PRODUCER);
    header.set("header", lineageHeader());
    ObjectNode unmatched = document("frontend-evidence-coverage-v1", "FRONTEND_EVIDENCE_COVERAGE");
    unmatched
        .put("recordType", "REQUEST_COVERAGE")
        .put("producer", EntryEvidencePublisher.PRODUCER);
    ObjectNode unmatchedPayload = unmatched.putObject("payload");
    unmatchedPayload
        .put("requestId", "request:unmatched")
        .put("resolution", "NO_MATCH")
        .putArray("entryIds")
        .removeAll();
    unmatchedPayload.putArray("includedEntryIds");
    unmatchedPayload
        .putObject("request")
        .put("requestId", "request:unmatched")
        .put("method", "GET")
        .put("url", "/orders/unmatched")
        .put("sourcePath", "web/src/pages/Orders.vue")
        .put("call", "this.getQueryParams()");
    unmatchedPayload
        .putArray("units")
        .addObject()
        .put("unitId", "unit:orders-mixin")
        .put("path", "web/src/mixins/OrdersMixin.js")
        .put("body", "getQueryParams() { return { pageNo: 1 }; }");
    ObjectNode ambiguous = document("frontend-evidence-coverage-v1", "FRONTEND_EVIDENCE_COVERAGE");
    ambiguous
        .put("recordType", "REQUEST_COVERAGE")
        .put("producer", EntryEvidencePublisher.PRODUCER);
    ObjectNode ambiguousPayload = ambiguous.putObject("payload");
    ambiguousPayload
        .put("requestId", "request:ambiguous")
        .put("resolution", "AMBIGUOUS")
        .putArray("entryIds")
        .add("entry:" + "0".repeat(63) + "1")
        .add("entry:" + "0".repeat(63) + "2");
    ambiguousPayload.putArray("includedEntryIds");
    ambiguousPayload
        .putArray("candidateEntryIds")
        .add("entry:" + "0".repeat(63) + "1")
        .add("entry:" + "0".repeat(63) + "2");
    ambiguousPayload
        .putObject("request")
        .put("requestId", "request:ambiguous")
        .put("method", "POST")
        .put("url", "/orders/search");
    ambiguousPayload.putArray("units");
    String content =
        String.join(
            "\n", List.of(line(json, header), line(json, unmatched), line(json, ambiguous), ""));
    return jsonlPayload(
        json,
        policies,
        "frontend-coverage.jsonl",
        "FRONTEND_EVIDENCE_COVERAGE",
        "frontend-evidence-coverage-v1",
        "frontend-evidence-coverage",
        content);
  }

  private static CanonicalModulePayload entryPayload(
      CanonicalJsonCodec json,
      CanonicalArtifactPolicyRegistry policies,
      String bodyEntryId,
      String fileEntryId) {
    return entryPayloadWithFileName(json, policies, bodyEntryId, entryFileName(fileEntryId));
  }

  private static CanonicalModulePayload entryPayloadWithFileName(
      CanonicalJsonCodec json,
      CanonicalArtifactPolicyRegistry policies,
      String bodyEntryId,
      String fileName) {
    ObjectNode body = document("entry-evidence-v1", "ENTRY_EVIDENCE");
    body.put("entryId", bodyEntryId);
    body.set("header", lineageHeader());
    body.set("sourceBasis", lineageHeader().get("sourceInventory").deepCopy());
    ObjectNode upstream = body.putObject("upstream");
    ObjectNode lineage = lineageHeader();
    upstream.set("applicationDiscovery", lineage.get("applicationDiscovery").deepCopy());
    upstream.set("navigationPublication", lineage.get("navigationPublication").deepCopy());
    upstream.set("persistencePublication", lineage.get("persistencePublication").deepCopy());
    upstream.set("frontendPublication", lineage.get("frontendPublication").deepCopy());
    httpEntry(body, bodyEntryId);
    boolean failed = bodyEntryId.endsWith("41");
    body.put("assemblyStatus", failed ? "NOT_ASSEMBLED" : "ASSEMBLED");
    if (failed) {
      body.putArray("limitations").addObject().put("code", "JDT_QUERY_FAILED");
    } else {
      body.putArray("limitations");
    }
    body.putObject("coverage").put("backend", "ASSEMBLED").put("frontend", "UNMATCHED");
    ObjectNode java = body.putObject("java");
    java.putArray("methods")
        .addObject()
        .put("methodKey", "method:shared")
        .put("body", "public void loadOrders() { support.load(); }");
    java.putArray("calls")
        .addObject()
        .put("entryId", bodyEntryId)
        .put("callKey", "call:" + bodyEntryId);
    java.putArray("supportingSources")
        .addObject()
        .put("path", "src/main/java/fixture/Support.java")
        .put("content", "final class Support { void load() {} }");
    java.putObject("technicalEnhancements").put("availability", "AVAILABLE").put("source", "r2");
    ObjectNode persistence = body.putObject("persistence");
    persistence
        .putArray("bindings")
        .addObject()
        .put("statementId", "mapper:orders.list")
        .put("status", "BOUND");
    persistence
        .putArray("statements")
        .addObject()
        .put("statementId", "mapper:orders.list")
        .put("xml", "<select id=\"list\">select * from orders</select>");
    persistence
        .putArray("resources")
        .addObject()
        .put("path", "src/main/resources/mapper/OrdersMapper.xml")
        .put(
            "content",
            "<mapper namespace=\"OrdersMapper\"><select id=\"list\">select * from orders</select></mapper>");
    persistence
        .putArray("sqlAnalyses")
        .addObject()
        .put("statementId", "mapper:orders.list")
        .put("analysisCopy", "select * from orders order by id desc")
        .put("status", "PARTIAL")
        .putArray("orderBy")
        .addObject()
        .put("expression", "id")
        .put("direction", "DESC")
        .put("nulls", "UNSPECIFIED");
    body.putArray("sourceRefs")
        .addObject()
        .put("reference", "source:entry")
        .put("kind", "SOURCE_FILE")
        .put("path", "src/main/java/fixture/Entry.java")
        .put("sourceSha256", "a".repeat(64));
    return standalonePayload(
        json, policies, fileName, "ENTRY_EVIDENCE", "entry-evidence-v1", "entry-evidence", body);
  }

  private static CanonicalModulePayload standalonePayload(
      CanonicalJsonCodec json,
      CanonicalArtifactPolicyRegistry policies,
      String fileName,
      String artifactType,
      String schema,
      String prefix,
      ObjectNode body)
      throws RuntimeException {
    String artifactId =
        prefix
            + ":"
            + sha256(
                concat(
                    frame("canonical-standalone-json-artifact-id-v1"),
                    frame(schema),
                    frame(artifactType),
                    frame(json.encodeCanonical(body).copyToByteArray())));
    body.put("artifactId", artifactId);
    policies.resolve(new ArtifactPolicyKey(artifactType, schema));
    return new CanonicalModulePayload(
        fileName,
        artifactType,
        schema,
        ArtifactId.parse(artifactId),
        CanonicalMediaType.APPLICATION_JSON,
        json.encodeCanonical(body));
  }

  private static CanonicalModulePayload jsonlPayload(
      CanonicalJsonCodec json,
      CanonicalArtifactPolicyRegistry policies,
      String fileName,
      String artifactType,
      String schema,
      String prefix,
      String content) {
    byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
    String artifactId =
        prefix
            + ":"
            + sha256(
                concat(
                    frame("canonical-jsonl-artifact-id-v1"),
                    frame(schema),
                    frame(artifactType),
                    frame(bytes)));
    policies.resolve(new ArtifactPolicyKey(artifactType, schema));
    return new CanonicalModulePayload(
        fileName,
        artifactType,
        schema,
        ArtifactId.parse(artifactId),
        CanonicalMediaType.APPLICATION_X_NDJSON,
        ImmutableBytes.copyOf(bytes));
  }

  private static String line(CanonicalJsonCodec json, ObjectNode object) {
    return new String(json.encodeCanonical(object).copyToByteArray(), StandardCharsets.UTF_8);
  }

  private static ModuleInstallRequest request(
      AnalysisStepModuleAddress address,
      ArtifactControls controls,
      List<CanonicalModulePayload> payloads) {
    return new ModuleInstallRequest(
        address, "v3", List.of(), controls, ModuleCompletionStatus.SUCCEEDED, List.of(), payloads);
  }

  private static CanonicalAnalysisStepPayload stepPayload(CanonicalModulePayload payload) {
    return new CanonicalAnalysisStepPayload(
        payload.fileName(),
        payload.artifactType(),
        payload.schemaVersion(),
        payload.artifactId(),
        payload.mediaType(),
        payload.canonicalUtf8());
  }

  private static CanonicalModuleArtifactStore moduleStore(
      RunStoreHandle handle,
      CanonicalJsonCodec json,
      CanonicalArtifactPolicyRegistry policies,
      int maxPayloadFiles) {
    return new FileSystemCanonicalModuleArtifactStore(
        handle,
        json,
        policies,
        new ArtifactStoreLimits(maxPayloadFiles, 16 * 1024 * 1024, 32 * 1024 * 1024, 256));
  }

  private static CanonicalAnalysisStepArtifactStore stepStore(
      RunStoreHandle handle,
      CanonicalJsonCodec json,
      CanonicalArtifactPolicyRegistry policies,
      int maxPayloadFiles) {
    return new FileSystemCanonicalAnalysisStepArtifactStore(
        handle,
        json,
        policies,
        new ArtifactStoreLimits(maxPayloadFiles, 16 * 1024 * 1024, 32 * 1024 * 1024, 256));
  }

  private static ArtifactControls controls(CanonicalArtifactPolicyRegistry policies) {
    return new ArtifactControls(digest('a'), digest('b'), digest('c'), null, policies.reference());
  }

  private static AnalysisStepPublicationReference upstreamReference(
      AnalysisStepKey step, char identity) {
    String value = String.valueOf(identity).repeat(64);
    return new AnalysisStepPublicationReference(
        new AnalysisStepPublicationAddress(RUN_ID, step),
        AnalysisStepArtifactRoot.parse("analysis-step-root:" + value),
        AnalysisStepReceiptId.parse("analysis-step-receipt:" + value),
        new Sha256Digest(value));
  }

  private static CanonicalArtifactPolicyRegistry entryEvidencePolicies(CanonicalJsonCodec json) {
    ObjectNode withoutId = JsonNodeFactory.instance.objectNode();
    withoutId.put("schemaVersion", "artifact-policy-registry-v2");
    ArrayNode policies = withoutId.putArray("policies");
    policy(
        policies.addObject(),
        "ENTRY_EVIDENCE",
        "entry-evidence-v1",
        "entry-evidence",
        "application/json",
        "STANDALONE_JSON");
    policy(
        policies.addObject(),
        "ENTRY_EVIDENCE_INDEX",
        "entry-evidence-index-v1",
        "entry-evidence-index",
        "application/json",
        "STANDALONE_JSON");
    policy(
        policies.addObject(),
        "FRONTEND_EVIDENCE_COVERAGE",
        "frontend-evidence-coverage-v1",
        "frontend-evidence-coverage",
        "application/x-ndjson",
        "CANONICAL_JSONL");
    ObjectNode document = withoutId.deepCopy();
    document.put(
        "artifactPolicyRegistryId",
        "artifact-policy-registry:"
            + sha256(
                concat(
                    frame("canonical-artifact-policy-registry-id-v2"),
                    frame(json.encodeCanonical(withoutId).copyToByteArray()))));
    return CanonicalArtifactPolicyRegistry.load(json.encodeCanonical(document), json);
  }

  private static void policy(
      ObjectNode policy,
      String artifactType,
      String schema,
      String prefix,
      String mediaType,
      String envelopeKind) {
    policy.put("artifactType", artifactType);
    policy.put("schemaVersion", schema);
    policy.put("artifactIdPrefix", prefix);
    policy.put("mediaType", mediaType);
    policy.put("envelopeKind", envelopeKind);
    policy.put("emptyJsonlAllowed", false);
    policy.put("publicContentExposure", "PATH_FREE_COMPLETE_UTF8");
  }

  private static ObjectNode document(String schema, String artifactType) {
    ObjectNode body = JSON.createObjectNode();
    body.put("schemaVersion", schema);
    body.put("artifactType", artifactType);
    body.put("producer", EntryEvidencePublisher.PRODUCER);
    return body;
  }

  private static ObjectNode lineageHeader() {
    ObjectNode header = JSON.createObjectNode();
    header.putObject("sourceInventory").putObject("publication").put("reference", "r0");
    header.putObject("applicationDiscovery").putObject("publication").put("reference", "r1");
    header.putObject("navigationPublication").putObject("publication").put("reference", "r2");
    header.putObject("persistencePublication").put("reference", "r3");
    header.putObject("frontendPublication").put("reference", "r1-frontend");
    header.put("sourceSnapshotId", "snapshot:" + "a".repeat(64));
    header.put("frontendStatus", "ENABLED");
    header.putArray("frontendFiles");
    header.putArray("frontendDiagnostics");
    header
        .putObject("profile")
        .put("maxEntryUtf8Bytes", 1_000_000)
        .put("maxPublicationUtf8Bytes", 4_000_000)
        .put("maxEntries", 65);
    return header;
  }

  private static void httpEntry(ObjectNode body, String entryId) {
    ObjectNode entry = body.putObject("entry");
    entry.put("entryId", entryId);
    entry.put("kind", "SPRING_MVC_HTTP");
    entry.put("protocol", "HTTP");
    entry.put("method", "GET");
    entry.putObject("methodCondition").put("kind", "EXPLICIT").putArray("methods").add("GET");
    entry.put("route", "/orders/list");
    entry.putArray("routeParts").add("orders").add("list");
    entry.put("handlerFqn", "fixture.OrdersController");
    entry.put("methodKey", "method:shared");
    entry
        .putObject("methodRange")
        .put("startOffsetUtf16", 0)
        .put("lengthUtf16", 42)
        .put("startLine", 1)
        .put("endLine", 4);
    entry.putArray("parameterNames");
    entry.set(
        "routeSourceExcerpts",
        JSON.createArrayNode()
            .add(sourceExcerpt("@RequestMapping(\"/orders\")"))
            .add(sourceExcerpt("@GetMapping(\"/list\")")));
  }

  private static ObjectNode sourceExcerpt(String text) {
    ObjectNode excerpt = JSON.createObjectNode();
    excerpt
        .putObject("locator")
        .put("fileId", "source:" + "a".repeat(64))
        .put("path", "src/main/java/fixture/Entry.java")
        .put("startByte", 0)
        .put("endByteExclusive", text.getBytes(StandardCharsets.UTF_8).length)
        .put("startLine", 1)
        .put("startColumn", 1)
        .put("endLine", 1)
        .put("endColumn", text.length());
    excerpt.put("rawUtf8", text);
    excerpt.put("rawUtf8Sha256", sha256(text.getBytes(StandardCharsets.UTF_8)));
    return excerpt;
  }

  private static List<String> entryIds(int count) {
    return java.util.stream.IntStream.range(0, count)
        .mapToObj(index -> "entry:" + String.format("%064x", index + 1))
        .toList();
  }

  private static String fileName(String entryId) {
    return "entry-" + entryId.substring("entry:".length()) + ".json";
  }

  private static String payloadEntryId(VerifiedCanonicalPayload payload) {
    try {
      return JSON.readTree(payload.canonicalUtf8().copyToByteArray()).path("entryId").asText();
    } catch (IOException failure) {
      throw new AssertionError("entry-evidence payload is not JSON", failure);
    }
  }

  private static String entryFileName(String fileEntryId) {
    return fileEntryId.contains("/") || fileEntryId.contains("\\")
        ? fileEntryId
        : fileName(fileEntryId);
  }

  private static List<Path> receiptFiles(Path root) throws Exception {
    if (!Files.exists(root.resolve("runs"))) {
      return List.of();
    }
    try (var paths = Files.walk(root.resolve("runs"))) {
      return paths
          .filter(path -> path.getFileName().toString().equals("module-receipt.json"))
          .toList();
    }
  }

  private static Sha256Digest digest(char value) {
    return new Sha256Digest(String.valueOf(value).repeat(64));
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] bytes) {
    return ByteBuffer.allocate(Long.BYTES + bytes.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(bytes.length)
        .put(bytes)
        .array();
  }

  private static byte[] concat(byte[]... values) {
    int size = 0;
    for (byte[] value : values) {
      size += value.length;
    }
    ByteBuffer result = ByteBuffer.allocate(size);
    for (byte[] value : values) {
      result.put(value);
    }
    return result.array();
  }
}
