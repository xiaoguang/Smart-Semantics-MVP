package org.sourceanalysis.app.analysis.interpretation.material;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.code.EngineDescriptor;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.JavaCodeSession;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.fact.ProvenCodeFactsExecutor;
import org.sourceanalysis.app.analysis.flow.BusinessFlowsExecutionRequest;
import org.sourceanalysis.app.analysis.flow.BusinessFlowsExecutor;
import org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjectionProfile;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilationProfile;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.flow.testsupport.BusinessFlowTestSupport;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsExecution;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Public-seam contract for the model-readable material checkpoint. */
class BusinessMaterialBuilderTest {

  @TempDir Path temporaryDirectory;

  @Test
  void formatsPersistedJdtMethodsCallsArgumentsControlsAndBoundariesWithoutReparsingSource()
      throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createForGuardedJavaCodeIndex(
            temporaryDirectory.resolve("jdt-material"))) {
      var sourceSet = fixture.sourceReader().reopen(fixture.sourceInventory());
      String source =
          sourceSet.documents().stream()
              .filter(value -> value.path().endsWith("OrderController.java"))
              .findFirst()
              .map(value -> new String(value.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8))
              .orElseThrow();
      var graphs =
          new ProgramGraphsExecution(
                  fixture.sourceReader(), fixture.moduleArtifacts(), fixture.stepArtifacts())
              .execute(
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  coherentJdtSession(sourceSet.snapshotId(), source),
                  fixture.artifactControls());
      var facts =
          new ProvenCodeFactsExecutor(
                  fixture.sourceReader(), fixture.moduleArtifacts(), fixture.stepArtifacts())
              .execute(fixture.sourceInventory(), fixture.applicationDiscovery(), graphs);
      BusinessFlowsReference flows =
          new BusinessFlowsExecutor(
                  fixture.sourceReader(), fixture.moduleArtifacts(), fixture.stepArtifacts())
              .execute(
                  new BusinessFlowsExecutionRequest(
                      fixture.sourceInventory(),
                      fixture.applicationDiscovery(),
                      graphs,
                      facts,
                      flowProfile(),
                      capsuleProfile()));

      BusinessMaterialSet set =
          new BusinessMaterialBuilder(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .build(
                  new BuildBusinessMaterialsRequest(
                      flows, new BusinessMaterialProfile(24, 200, 100_000)))
              .materialSet();

      assertThat(set.entryCoverage()).hasSize(2);
      assertThat(set.entryCoverage())
          .allSatisfy(
              coverage ->
                  assertThat(coverage.disposition())
                      .isIn("ANALYZED_MATERIAL", "MATERIAL_WITH_GAPS"));
      assertThat(set.materials()).isNotEmpty();
      String modelInput =
          set.materials().stream().map(BusinessMaterial::modelPacket).toList().toString();
      assertThat(modelInput)
          .contains(
              "M1",
              "M2",
              "C1",
              "com.example.OrderController",
              "com.example.OrderService",
              "void approve(String status)",
              "if (status == null)",
              "approvalClient.record(status)",
              "实参[0]=status",
              "形参[0]=status:String",
              "候选",
              "DECLARATION_ONLY",
              "RETURN")
          .doesNotContain("OrderController.java", "startLine", "sha256");
    }
  }

  @Test
  void turnsPersistedFlowsIntoReadablePacketsAndKeepsSourceDetailsOutOfModelInput()
      throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("materials"))) {
      BusinessFlowsReference flows = BusinessFlowTestSupport.publishBusinessFlows(fixture);

      BusinessMaterialBuildResult result =
          new BusinessMaterialBuilder(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .build(
                  new BuildBusinessMaterialsRequest(
                      flows, new BusinessMaterialProfile(8, 24, 12_000)));

      assertThat(result.materialSet().materials())
          .singleElement()
          .satisfies(
              material -> {
                assertThat(material.entryIds()).hasSize(2);
                assertThat(material.modelPacket().context())
                    .contains("HTTP POST /orders/cancel", "HTTP POST /orders/approve");
              });
      assertThat(result.materialSet().entryCoverage()).hasSize(2);
      assertThat(result.materialSet().entryCoverage())
          .allSatisfy(
              coverage -> {
                BusinessMaterial material =
                    result.materialSet().materials().stream()
                        .filter(value -> coverage.materialId().equals(value.materialId()))
                        .findFirst()
                        .orElseThrow();
                assertThat(coverage.disposition())
                    .isEqualTo(
                        material.hasSubstantiveLimitation()
                            ? "MATERIAL_WITH_GAPS"
                            : "ANALYZED_MATERIAL");
              });
      assertThat(result.materialSet().materials())
          .allSatisfy(
              material -> {
                assertThat(material.materialMode()).isEqualTo(BusinessMaterialMode.FLOW_PREFERRED);
                assertThat(material.technicalObservations()).isNotEmpty();
                assertThat(material.sourceRefs()).isNotEmpty();
                assertThat(material.modelPacket().allowlistedRefs()).isNotEmpty();
                assertThat(
                        material.modelPacket().allowlistedRefs().stream()
                            .map(ModelActivityPacket.AllowlistedReference::ref)
                            .toList())
                    .doesNotHaveDuplicates();
                assertThat(material.modelPacket().toString())
                    .doesNotContain(
                        ".java",
                        "sha256",
                        "startLine",
                        "endLine",
                        "proof:",
                        "evidence-node:",
                        "data-flow-node:",
                        "program-graph:",
                        "java-parameter-symbol-v1:",
                        "call-node:",
                        "flow:",
                        "fact-gap:",
                        "material:");
              });

      Set<String> refs = new HashSet<>();
      result
          .materialSet()
          .materials()
          .forEach(material -> material.sourceRefs().forEach(ref -> refs.add(ref.ref())));
      assertThat(refs).hasSizeGreaterThanOrEqualTo(2);

      ReopenedModulePublication reopened = fixture.moduleArtifacts().reopen(result.checkpoint());
      String jsonl =
          new String(
              reopened.payloads().stream()
                  .filter(value -> value.descriptor().fileName().equals("business-materials.jsonl"))
                  .findFirst()
                  .orElseThrow()
                  .canonicalUtf8()
                  .copyToByteArray(),
              StandardCharsets.UTF_8);
      List<JsonNode> lines =
          Arrays.stream(jsonl.stripTrailing().split("\\n"))
              .map(
                  line ->
                      new CanonicalJsonCodec()
                          .parseCanonical(
                              ImmutableBytes.copyOf(line.getBytes(StandardCharsets.UTF_8))))
              .toList();
      assertThat(lines).hasSize(3);
      List<JsonNode> materialRecords =
          lines.stream()
              .filter(line -> line.path("recordType").asText().equals("BUSINESS_MATERIAL"))
              .toList();
      List<JsonNode> coverageRecords =
          lines.stream()
              .filter(line -> line.path("recordType").asText().equals("ENTRY_COVERAGE"))
              .toList();
      assertThat(materialRecords).hasSize(1);
      assertThat(coverageRecords).hasSize(2);
      assertThat(materialRecords.get(0).path("sourceRefs").get(0).path("file").asText())
          .endsWith(".java");
      assertThat(materialRecords)
          .allSatisfy(
              record -> {
                JsonNode packet = record.path("modelPacket");
                assertThat(packet.isObject()).isTrue();
                assertThat(packet.fieldNames())
                    .toIterable()
                    .containsExactlyInAnyOrder(
                        "context", "technicalObservations", "allowlistedRefs", "limitations");
                assertThat(packet.toString())
                    .doesNotContain("file", "startLine", "endLine", "sha256", "proof:");
              });
      assertThat(coverageRecords)
          .allSatisfy(
              record -> {
                assertThat(record.path("entryId").asText()).isNotBlank();
                assertThat(record.path("disposition").asText()).isNotBlank();
                assertThat(record.path("materialId").asText()).isNotBlank();
              });
    }
  }

  @Test
  void marksAnOverBudgetEntryWithoutCreatingAComparableModelPacket() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("over-budget"))) {
      BusinessFlowsReference flows = BusinessFlowTestSupport.publishBusinessFlows(fixture);

      BusinessMaterialBuildResult result =
          new BusinessMaterialBuilder(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .build(
                  new BuildBusinessMaterialsRequest(flows, new BusinessMaterialProfile(8, 24, 1)));

      assertThat(result.materialSet().materials()).isEmpty();
      assertThat(result.materialSet().entryCoverage())
          .allSatisfy(
              entry -> {
                assertThat(entry.disposition()).isEqualTo("NOT_MATERIALIZED");
                assertThat(entry.reasonCode()).isEqualTo("SOURCE_MATERIAL_OVER_BUDGET");
                assertThat(entry.materialId()).isNull();
              });
    }
  }

  @Test
  void usesPersistedStrictContextAndSourceWithoutReparsingItOrNamingTheBusiness() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("code-outline"))) {
      BusinessFlowsReference flows = BusinessFlowTestSupport.publishBusinessFlows(fixture);

      BusinessMaterialBuildResult result =
          new BusinessMaterialBuilder(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .build(
                  new BuildBusinessMaterialsRequest(
                      flows, new BusinessMaterialProfile(8, 24, 12_000)));

      BusinessMaterial guardedActivity =
          result.materialSet().materials().stream()
              .filter(
                  material ->
                      material.modelPacket().allowlistedRefs().stream()
                          .anyMatch(reference -> reference.snippet().contains("status == null")))
              .findFirst()
              .orElseThrow();

      assertThat(guardedActivity.modelPacket().allowlistedRefs())
          .extracting(ModelActivityPacket.AllowlistedReference::snippet)
          .anyMatch(value -> value.contains("void approve(String status)"))
          .anyMatch(value -> value.contains("approvalClient.record(status)"));
      assertThat(guardedActivity.modelPacket().technicalObservations())
          .anyMatch(value -> value.startsWith("源码条件：") && value.contains("status == null"))
          .anyMatch(
              value ->
                  value.startsWith("源码调用：")
                      && value.contains("ApprovalClient#record(java.lang.String)"))
          .noneMatch(value -> value.contains("订单") || value.contains("审批"));
    }
  }

  @Test
  void keepsCallerArgumentsBoundaryAndSourceTogetherInOneModelPacket() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithGuardedApprove(
            temporaryDirectory.resolve("connected-entry-context"))) {
      BusinessFlowsReference flows = BusinessFlowTestSupport.publishBusinessFlows(fixture);

      BusinessMaterial approve =
          new BusinessMaterialBuilder(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
                  .build(
                      new BuildBusinessMaterialsRequest(
                          flows, new BusinessMaterialProfile(8, 24, 12_000)))
                  .materialSet()
                  .materials()
                  .stream()
                  .filter(
                      material ->
                          material.sourceRefs().stream()
                              .anyMatch(
                                  reference ->
                                      reference.snippet().contains("orderService.approve")))
                  .findFirst()
                  .orElseThrow();

      assertThat(approve.modelPacket().allowlistedRefs())
          .extracting(ModelActivityPacket.AllowlistedReference::snippet)
          .anySatisfy(snippet -> assertThat(snippet).contains("orderService.approve(status)"))
          .anySatisfy(snippet -> assertThat(snippet).contains("approvalClient.record(status)"));
      assertThat(approve.modelPacket().technicalObservations())
          .anySatisfy(
              observation ->
                  assertThat(observation)
                      .contains(
                          "OrderController#approve", "OrderService#approve", "java.lang.String"))
          .anySatisfy(
              observation ->
                  assertThat(observation)
                      .contains(
                          "OrderService#approve",
                          "ApprovalClient#record",
                          "java.lang.String",
                          "边界"))
          .anySatisfy(observation -> assertThat(observation).contains("status == null"))
          .anySatisfy(observation -> assertThat(observation).contains("返回路径"));
    }
  }

  private static FlowCompilationProfile flowProfile() {
    return new FlowCompilationProfile(
        reference("flow-profile", 'a', 'b'), 16, 8, 64, 96, 32, 64, 256);
  }

  private static CapsuleProjectionProfile capsuleProfile() {
    return new CapsuleProjectionProfile(
        reference("capsule-profile", 'c', 'd'), 16, 32, 4_096, 100_000);
  }

  private static ArtifactReference reference(String prefix, char identity, char content) {
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + String.valueOf(identity).repeat(64)),
        Sha256Digest.parse(String.valueOf(content).repeat(64)));
  }

  private static JavaCodeSession coherentJdtSession(String snapshotId, String source) {
    return new JavaCodeSession() {
      @Override
      public JavaDeclarationCatalog catalog() {
        return new JavaDeclarationCatalog(
            snapshotId,
            List.of("src/main/java/com/example/OrderController.java"),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Map.of());
      }

      @Override
      public EntryCodeContext collect(EntrySeed entry) {
        String methodName = entry.trigger().contains("cancel") ? "cancel" : "approve";
        String rootKey = entry.methodKey();
        String serviceKey = "method:service:" + methodName;
        String boundaryKey = "method:boundary:" + methodName;
        SourceRange rootRange = methodRange(source, "void " + methodName + "(String status)", 1);
        SourceRange serviceRange = methodRange(source, "void " + methodName + "(String status)", 2);
        SourceRange boundaryRange =
            methodRange(source, "void record(String status)", methodName.equals("approve") ? 1 : 2);
        EntryCodeContext.MethodCode root =
            methodCode(
                rootKey,
                "com.example.OrderController",
                methodName,
                rootRange,
                source,
                List.of(),
                List.of());
        List<EntryCodeContext.Control> controls = new java.util.ArrayList<>();
        List<EntryCodeContext.Exit> exits = new java.util.ArrayList<>();
        if (methodName.equals("approve")) {
          controls.add(
              new EntryCodeContext.Control(
                  "IF",
                  "status == null",
                  rangeOf(source, "if (status == null)", serviceRange),
                  null));
          exits.add(
              new EntryCodeContext.Exit("RETURN", null, rangeOf(source, "return;", serviceRange)));
        }
        EntryCodeContext.MethodCode service =
            methodCode(
                serviceKey,
                "com.example.OrderService",
                methodName,
                serviceRange,
                source,
                List.copyOf(controls),
                List.copyOf(exits));
        EntryCodeContext.MethodCode boundary =
            new EntryCodeContext.MethodCode(
                boundaryKey,
                "METHOD",
                methodName.equals("approve")
                    ? "com.example.ApprovalClient"
                    : "com.example.CancellationClient",
                "record",
                "void record(String status)",
                null,
                List.of(parameter()),
                "void",
                List.of(),
                List.of(),
                new EntryCodeContext.SourceSource(
                    "src/main/java/com/example/OrderController.java",
                    boundaryRange,
                    slice(source, boundaryRange)),
                false,
                List.of(),
                List.of());
        EntryCodeContext.CallSite rootCall =
            call(
                "call:root:" + methodName,
                rootKey,
                rangeOf(source, "orderService." + methodName + "(status)", rootRange),
                "orderService." + methodName + "(status)",
                serviceKey,
                "BODY_INCLUDED",
                null,
                List.of());
        EntryCodeContext.CallSite boundaryCall =
            call(
                "call:boundary:" + methodName,
                serviceKey,
                rangeOf(
                    source,
                    (methodName.equals("approve") ? "approvalClient" : "cancellationClient")
                        + ".record(status)",
                    serviceRange),
                (methodName.equals("approve") ? "approvalClient" : "cancellationClient")
                    + ".record(status)",
                boundaryKey,
                "DECLARATION_ONLY",
                "runtime implementation is outside the repository",
                methodName.equals("approve") ? List.of(0) : List.of());
        SourceRange fieldRange =
            rangeOf(
                source,
                methodName.equals("approve")
                    ? "private final ApprovalClient approvalClient = null;"
                    : "private final CancellationClient cancellationClient = null;",
                new SourceRange(0, source.length(), 1, line(source, source.length())));
        return new EntryCodeContext(
            EntryCodeContext.SCHEMA_VERSION,
            entry.entryId(),
            rootKey,
            List.of(root, service, boundary),
            List.of(rootCall, boundaryCall),
            List.of(
                new EntryCodeContext.SupportingSource(
                    "FIELD",
                    new EntryCodeContext.SourceSource(
                        "src/main/java/com/example/OrderController.java",
                        fieldRange,
                        slice(source, fieldRange)),
                    List.of(serviceKey),
                    "receiver declaration")),
            List.of(
                new EntryCodeContext.Limitation(
                    "EXTERNAL_IMPLEMENTATION_NOT_AVAILABLE",
                    "boundary implementation is not present in the frozen repository",
                    List.of(serviceKey),
                    List.of(boundaryCall.callKey()))),
            new EntryCodeContext.TechnicalEnhancements(
                EntryCodeContext.Availability.NOT_PRODUCED,
                "strict graphs were not requested",
                List.of(),
                List.of(),
                null));
      }

      @Override
      public EngineDescriptor descriptor() {
        return new EngineDescriptor(
            "jdt", "test-adapter-v1", Map.of("jdtls", "1.61.0"), "17", List.of("METHODS"));
      }

      @Override
      public void close() {}
    };
  }

  private static EntryCodeContext.MethodCode methodCode(
      String key,
      String owner,
      String name,
      SourceRange range,
      String source,
      List<EntryCodeContext.Control> controls,
      List<EntryCodeContext.Exit> exits) {
    return new EntryCodeContext.MethodCode(
        key,
        "METHOD",
        owner,
        name,
        "void " + name + "(String status)",
        null,
        List.of(parameter()),
        "void",
        List.of(),
        List.of(),
        new EntryCodeContext.SourceSource(
            "src/main/java/com/example/OrderController.java", range, slice(source, range)),
        true,
        controls,
        exits);
  }

  private static JavaDeclarationCatalog.ParameterView parameter() {
    return new JavaDeclarationCatalog.ParameterView(0, "status", "String", false, List.of());
  }

  private static EntryCodeContext.CallSite call(
      String key,
      String caller,
      SourceRange site,
      String expression,
      String target,
      String expansion,
      String reason,
      List<Integer> enclosingControls) {
    return new EntryCodeContext.CallSite(
        key,
        caller,
        "METHOD",
        site,
        site,
        expression,
        expression.substring(0, expression.indexOf('.')),
        List.of(new EntryCodeContext.ActualArgument(0, "status")),
        enclosingControls,
        false,
        List.of(
            new EntryCodeContext.CallTarget(
                target,
                List.of("DECLARATION"),
                target,
                List.of("ENGINE_BINDING"),
                expansion,
                reason,
                List.of(new EntryCodeContext.ArgumentAssociation(List.of(0), 0, "POSITIONAL")))),
        "LOCATED",
        null);
  }

  private static SourceRange methodRange(String source, String marker, int occurrence) {
    int start = nthIndexOf(source, marker, occurrence);
    int open = source.indexOf('{', start);
    if (open < 0) {
      int end = source.indexOf(';', start) + 1;
      return range(source, start, end);
    }
    int depth = 0;
    for (int cursor = open; cursor < source.length(); cursor++) {
      char value = source.charAt(cursor);
      if (value == '{') depth++;
      if (value == '}' && --depth == 0) return range(source, start, cursor + 1);
    }
    throw new IllegalArgumentException("method body is incomplete");
  }

  private static SourceRange rangeOf(String source, String needle, SourceRange owner) {
    int start = source.indexOf(needle, owner.startOffsetUtf16());
    if (start < 0 || start + needle.length() > owner.startOffsetUtf16() + owner.lengthUtf16()) {
      throw new IllegalArgumentException("source fragment is outside owner");
    }
    return range(source, start, start + needle.length());
  }

  private static SourceRange range(String source, int start, int end) {
    return new SourceRange(start, end - start, line(source, start), line(source, end));
  }

  private static int line(String source, int offset) {
    return 1
        + (int)
            source
                .substring(0, Math.min(offset, source.length()))
                .chars()
                .filter(c -> c == '\n')
                .count();
  }

  private static String slice(String source, SourceRange range) {
    return source.substring(
        range.startOffsetUtf16(), range.startOffsetUtf16() + range.lengthUtf16());
  }

  private static int nthIndexOf(String source, String needle, int occurrence) {
    int cursor = -1;
    for (int index = 0; index < occurrence; index++) {
      cursor = source.indexOf(needle, cursor + 1);
      if (cursor < 0) throw new IllegalArgumentException("source marker not found: " + needle);
    }
    return cursor;
  }
}
