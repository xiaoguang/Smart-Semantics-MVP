package org.sourceanalysis.app.analysis.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.interpretation.material.SourceReference;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactDescriptor;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyKey;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicy;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleArtifactRoot;
import org.sourceanalysis.app.artifact.ModuleInstallDisposition;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ModuleReceipt;
import org.sourceanalysis.app.artifact.ModuleReceiptId;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/** Guards deterministic publication and fresh reopen of the five Step07 process files. */
class BusinessProcessPublicationTest {

  @Test
  void publishesAndFreshReopensTheDeterministicProcessDocument() {
    ProcessDiscoveryResult discovered = discoveryResult();
    CapturingModuleStore store =
        new CapturingModuleStore(discovered.activityCheckpoint(), discovered.materialCheckpoint());

    BusinessProcessPublication published =
        new CanonicalBusinessProcessPublisher(store).publish(discovered);
    BusinessProcessPublication reopened =
        new BusinessProcessCheckpointReader(store).reopen(published.checkpoint());

    assertThat(store.installedFileNames())
        .containsExactlyInAnyOrder(
            "business-processes.md",
            "process-coverage.json",
            "repository-business-process-catalog.json",
            "source-refs.jsonl",
            "sources.md");
    assertThat(reopened).isEqualTo(published);
    assertThat(reopened.businessProcessesMarkdown())
        .contains("条件：当前状态为0；处理：允许修改订单；否则：拒绝修改")
        .doesNotContain("// create concrete source");
    assertThat(reopened.sourceReferences())
        .extracting(SourceReference::ref)
        .containsExactly("S1", "S2", "S3");
    assertThat(new BusinessProcessCheckpointReader(store).rerender(reopened))
        .isEqualTo(reopened.businessProcessesMarkdown());
  }

  @Test
  void publishesExactlyFiveV2FilesAndPreservesReadableProcessFieldsInJson() throws Exception {
    ProcessDiscoveryResult discovered = discoveryResult();
    CapturingModuleStore store =
        new CapturingModuleStore(discovered.activityCheckpoint(), discovered.materialCheckpoint());

    new CanonicalBusinessProcessPublisher(store).publish(discovered);

    Map<String, VerifiedCanonicalPayload> payloads = store.installedPayloadsByFile();
    assertThat(store.installedModuleVersion()).isEqualTo("v2");
    assertThat(payloads)
        .containsOnlyKeys(
            "repository-business-process-catalog.json",
            "process-coverage.json",
            "business-processes.md",
            "source-refs.jsonl",
            "sources.md");
    assertThat(payloads.get("repository-business-process-catalog.json").descriptor())
        .satisfies(
            descriptor -> {
              assertThat(descriptor.artifactType())
                  .isEqualTo("REPOSITORY_KNOWLEDGE_BUSINESS_PROCESS_CATALOG");
              assertThat(descriptor.schemaVersion())
                  .isEqualTo("repository-business-process-catalog-v2");
            });
    assertThat(payloads.get("process-coverage.json").descriptor())
        .satisfies(
            descriptor -> {
              assertThat(descriptor.artifactType())
                  .isEqualTo("REPOSITORY_KNOWLEDGE_PROCESS_COVERAGE");
              assertThat(descriptor.schemaVersion())
                  .isEqualTo("repository-business-process-coverage-v2");
            });
    assertThat(payloads.get("business-processes.md").descriptor())
        .satisfies(
            descriptor -> {
              assertThat(descriptor.artifactType())
                  .isEqualTo("REPOSITORY_KNOWLEDGE_BUSINESS_PROCESSES_MARKDOWN");
              assertThat(descriptor.schemaVersion())
                  .isEqualTo("repository-business-process-markdown-v2");
            });
    assertThat(payloads.get("source-refs.jsonl").descriptor().schemaVersion())
        .isEqualTo("repository-business-process-source-references-v1");
    assertThat(payloads.get("sources.md").descriptor())
        .satisfies(
            descriptor -> {
              assertThat(descriptor.artifactType())
                  .isEqualTo("REPOSITORY_KNOWLEDGE_BUSINESS_PROCESS_SOURCES_MARKDOWN");
              assertThat(descriptor.schemaVersion())
                  .isEqualTo("repository-business-process-sources-markdown-v1");
            });

    JsonNode catalog = json(payloads.get("repository-business-process-catalog.json"));
    JsonNode process = catalog.path("processes").get(0);
    assertThat(process.path("stages").get(0).path("narrative").asText())
        .isEqualTo("创建订单：接收订单明细，生成状态为0的订单。");
    assertThat(jsonStrings(process.path("businessRules").get(0).path("activityUseIds")))
        .containsExactlyInAnyOrder(
            process.path("activityUses").get(0).path("activityUseId").asText(),
            process.path("activityUses").get(1).path("activityUseId").asText(),
            process.path("activityUses").get(2).path("activityUseId").asText());

    JsonNode coverage = json(payloads.get("process-coverage.json"));
    assertThat(
            findByTextField(
                    coverage.path("activityDispositions"), "activityId", "activity:create")
                .path("name")
                .asText())
        .isEqualTo("创建销售订单");
  }

  @Test
  void rendersNarrativeAndScopedRuleSentencesWithAnchoredSources() {
    ProcessDiscoveryResult discovered = discoveryResult();
    String markdown =
        BusinessProcessMarkdownRenderer.render(
            discovered.catalog(), discovered.coverage(), discovered.sourceReferences());
    String rules = markdown.substring(markdown.indexOf("### 重要业务规则"), markdown.indexOf("### 结束结果"));

    assertThat(markdown)
        .contains("创建订单：接收订单明细，生成状态为0的订单。")
        .contains("[查看依据](#process-1-stage-1)")
        .contains("[查看依据](#process-1-rule-1)")
        .doesNotContain("1. **创建订单**（")
        .doesNotContain("2. **修改订单**（")
        .doesNotMatch("(?m)^- \\[S\\d+\\]$");
    assertThat(rules)
        .contains("条件：当前状态为0；处理：允许修改订单；否则：拒绝修改")
        .containsPattern("适用[^\\n]*销售订单")
        .doesNotContain("activity-use:");
  }

  @Test
  void sourcesMarkdownContainsDeterministicAnchorsAndCompleteSourceReferences() throws Exception {
    ProcessDiscoveryResult discovered = discoveryResult();
    CapturingModuleStore store =
        new CapturingModuleStore(discovered.activityCheckpoint(), discovered.materialCheckpoint());

    new CanonicalBusinessProcessPublisher(store).publish(discovered);

    Map<String, VerifiedCanonicalPayload> payloads = store.installedPayloadsByFile();
    assertThat(payloads).containsKey("sources.md");
    String sources = utf8(payloads.get("sources.md"));
    assertThat(sources)
        .contains("S1")
        .contains("src/main/java/example/OrderService.java")
        .contains(discovered.sourceReferences().get(0).snippet())
        .containsPattern("(?m)^.*(?:id=\\\"s1\\\"|\\{#s1\\}|#s1).*\\R?");
    assertThat(sources.indexOf("S1")).isLessThan(sources.indexOf("S2"));
    assertThat(sources.indexOf("S2")).isLessThan(sources.indexOf("S3"));
    assertThat(sources).containsPattern("10(?:–|-)12");
  }

  @Test
  void readerReopensExactFiveFilesAndRerendersBothMarkdownFilesByteIdentically() throws Exception {
    ProcessDiscoveryResult discovered = discoveryResult();
    CapturingModuleStore store =
        new CapturingModuleStore(discovered.activityCheckpoint(), discovered.materialCheckpoint());
    BusinessProcessPublication published =
        new CanonicalBusinessProcessPublisher(store).publish(discovered);

    BusinessProcessCheckpointReader reader = new BusinessProcessCheckpointReader(store);
    BusinessProcessPublication reopened = reader.reopen(published.checkpoint());
    String persistedSources = utf8(store.installedPayloadsByFile().get("sources.md"));

    assertThat(reopened).isEqualTo(published);
    assertThat(reader.rerender(reopened)).isEqualTo(published.businessProcessesMarkdown());
    assertThat(sourceMarkdown(reopened)).isEqualTo(persistedSources);
    assertThat(rerenderSources(reader, reopened)).isEqualTo(persistedSources);
  }

  @Test
  void mainMarkdownUsesOneLocalEvidenceLinkAndACompleteProcessSourceIndex() {
    ProcessDiscoveryResult discovered = discoveryResult();
    RepositoryBusinessProcessCatalog.BusinessProcess process =
        discovered.catalog().processes().get(0);
    String markdown =
        BusinessProcessMarkdownRenderer.render(
            discovered.catalog(), discovered.coverage(), discovered.sourceReferences());
    int processStart = markdown.indexOf("## " + process.name());
    int processEnd = markdown.indexOf("\n## ", processStart + 1);
    String processMarkdown =
        markdown.substring(processStart, processEnd < 0 ? markdown.length() : processEnd);

    assertThat(processMarkdown).contains("过程来源索引");
    int sourceIndexStart = processMarkdown.indexOf("过程来源索引");
    String mainProcessMarkdown = processMarkdown.substring(0, sourceIndexStart);
    List<String> evidenceLines =
        mainProcessMarkdown.lines().filter(line -> line.contains("查看依据")).toList();
    int expectedEvidenceLinks =
        (process.sourceRefs().isEmpty() ? 0 : 1)
            + (int) process.stages().stream().filter(stage -> !stage.sourceRefs().isEmpty()).count()
            + (int)
                process.businessRules().stream()
                    .filter(rule -> !rule.sourceRefs().isEmpty())
                    .count();
    assertThat(evidenceLines).hasSize(expectedEvidenceLinks);
    assertThat(evidenceLines)
        .allSatisfy(
            line ->
                assertThat(line)
                    .containsPattern("\\[查看依据\\]\\(#[^)]+\\)")
                    .doesNotContain("sources.md#"));
    assertThat(mainProcessMarkdown).doesNotContain("sources.md#");

    String sourceIndex = processMarkdown.substring(sourceIndexStart);
    discovered
        .sourceReferences()
        .forEach(
            source ->
                assertThat(sourceIndex)
                    .contains(source.ref())
                    .contains(source.file())
                    .contains(String.valueOf(source.startLine()))
                    .contains(String.valueOf(source.endLine()))
                    .contains("sources.md#" + source.ref().toLowerCase()));
  }

  @Test
  void readerRequiresAnExactUniqueClosedSourceReferenceSetBeforeMarkdownChecks() throws Exception {
    ProcessDiscoveryResult discovered = discoveryResult();
    CapturingModuleStore missingSourceStore =
        new CapturingModuleStore(discovered.activityCheckpoint(), discovered.materialCheckpoint());
    BusinessProcessPublication missingSourcePublication =
        new CanonicalBusinessProcessPublisher(missingSourceStore).publish(discovered);
    ObjectNode catalogJson =
        (ObjectNode)
            json(missingSourceStore.installedPayloadsByFile().get("repository-business-process-catalog.json"));
    ((ObjectNode) catalogJson.withArray("processes").get(0))
        .withArray("sourceRefs")
        .add("S999");
    ObjectNode catalogBody = catalogJson.deepCopy();
    catalogBody.remove(List.of("artifactId", "artifactType", "schemaVersion"));
    RepositoryBusinessProcessCatalog catalogWithMissingSource =
        new ObjectMapper().treeToValue(catalogBody, RepositoryBusinessProcessCatalog.class);
    missingSourceStore.replaceInstalledPayload(
        "repository-business-process-catalog.json", canonical(catalogJson));
    missingSourceStore.replaceInstalledPayload(
        "business-processes.md",
        ImmutableBytes.copyOf(
            BusinessProcessMarkdownRenderer
                .render(
                    catalogWithMissingSource,
                    missingSourcePublication.coverage(),
                    missingSourcePublication.sourceReferences())
                .getBytes(StandardCharsets.UTF_8)));

    assertThatThrownBy(
            () ->
                new BusinessProcessCheckpointReader(missingSourceStore)
                    .reopen(missingSourcePublication.checkpoint()))
        .isInstanceOf(BusinessProcessCheckpointException.class)
        .hasMessage("BUSINESS_PROCESS_SOURCE_REFERENCE_CLOSURE_INVALID");
  }

  @Test
  void readerRejectsDuplicateSourceReferenceRecordsEvenWhenMarkdownMatches() {
    ProcessDiscoveryResult discovered = discoveryResult();
    CapturingModuleStore duplicateSourceStore =
        new CapturingModuleStore(discovered.activityCheckpoint(), discovered.materialCheckpoint());
    BusinessProcessPublication duplicateSourcePublication =
        new CanonicalBusinessProcessPublisher(duplicateSourceStore).publish(discovered);
    String sourceRefs =
        utf8(duplicateSourceStore.installedPayloadsByFile().get("source-refs.jsonl"));
    int firstLineEnd = sourceRefs.indexOf('\n') + 1;
    duplicateSourceStore.replaceInstalledPayload(
        "source-refs.jsonl",
        ImmutableBytes.copyOf((sourceRefs.substring(0, firstLineEnd) + sourceRefs).getBytes(StandardCharsets.UTF_8)));
    List<SourceReference> duplicateSources = new ArrayList<>(discovered.sourceReferences());
    duplicateSources.add(0, duplicateSources.get(0));
    duplicateSourceStore.replaceInstalledPayload(
        "sources.md",
        ImmutableBytes.copyOf(SourcesMarkdownRenderer.render(duplicateSources).getBytes(StandardCharsets.UTF_8)));

    assertThatThrownBy(
            () ->
                new BusinessProcessCheckpointReader(duplicateSourceStore)
                    .reopen(duplicateSourcePublication.checkpoint()))
        .isInstanceOf(BusinessProcessCheckpointException.class)
        .hasMessage("BUSINESS_PROCESS_SOURCE_REFERENCE_CLOSURE_INVALID");
  }

  @Test
  void ruleRenderingDoesNotDuplicateFreeFormChineseConditionParticles() {
    ProcessDiscoveryResult discovered = discoveryResult();
    RepositoryBusinessProcessCatalog.BusinessProcess original =
        discovered.catalog().processes().get(0);
    RepositoryBusinessProcessCatalog.BusinessRule originalRule = original.businessRules().get(0);
    List<RepositoryBusinessProcessCatalog.BusinessRule> rules =
        List.of(
            new RepositoryBusinessProcessCatalog.BusinessRule(
                originalRule.subject(),
                "收到订单请求时",
                originalRule.actionOrDecision(),
                originalRule.otherwise(),
                originalRule.result(),
                originalRule.certainty(),
                originalRule.activityUseIds(),
                originalRule.statementRefs(),
                originalRule.sourceRefs()),
            new RepositoryBusinessProcessCatalog.BusinessRule(
                originalRule.subject(),
                "完成重复检查后",
                originalRule.actionOrDecision(),
                originalRule.otherwise(),
                originalRule.result(),
                originalRule.certainty(),
                originalRule.activityUseIds(),
                originalRule.statementRefs(),
                originalRule.sourceRefs()));
    RepositoryBusinessProcessCatalog.BusinessProcess withFreeFormConditions =
        new RepositoryBusinessProcessCatalog.BusinessProcess(
            original.processId(),
            original.name(),
            original.purpose(),
            original.scope(),
            original.participants(),
            original.businessObjects(),
            original.activityUses(),
            original.stages(),
            original.branches(),
            rules,
            original.endResults(),
            original.supportActivityUseIds(),
            original.knowledgeItems(),
            original.pendingConnections(),
            original.sourceRefs());
    List<RepositoryBusinessProcessCatalog.BusinessProcess> processes =
        new ArrayList<>(discovered.catalog().processes());
    processes.set(0, withFreeFormConditions);
    RepositoryBusinessProcessCatalog catalog =
        new RepositoryBusinessProcessCatalog(
            discovered.catalog().businessAreas(),
            discovered.catalog().aliases(),
            processes,
            discovered.catalog().processRelations(),
            discovered.catalog().standaloneActivityIds(),
            discovered.catalog().unclassifiedActivityIds(),
            discovered.catalog().directActivityKnowledgeItems(),
            discovered.catalog().pendingConfirmations());

    String rulesMarkdown =
        BusinessProcessMarkdownRenderer.render(
                catalog, discovered.coverage(), discovered.sourceReferences())
            .substring(
                BusinessProcessMarkdownRenderer.render(
                        catalog, discovered.coverage(), discovered.sourceReferences())
                    .indexOf("### 重要业务规则"));
    assertThat(rulesMarkdown)
        .contains("条件：收到订单请求时；处理：" + originalRule.actionOrDecision())
        .contains("条件：完成重复检查后；处理：" + originalRule.actionOrDecision())
        .doesNotContain("当收到订单请求时时")
        .doesNotContain("当完成重复检查后时");
  }

  @Test
  void sourcesMarkdownRejectsUnsafeRefsAndDoesNotBreakBacktickPaths() {
    SourceReference unsafeRef =
        new SourceReference("S<1>", "src/<orders>&\"quotes\".java", 1, 2, "snippet");
    assertThatThrownBy(() -> SourcesMarkdownRenderer.render(List.of(unsafeRef)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void sourcesMarkdownDoesNotBreakBacktickPaths() {
    SourceReference backtickPath =
        new SourceReference("S1", "src/`orders`.java", 10, 12, "snippet");
    String markdown = SourcesMarkdownRenderer.render(List.of(backtickPath));
    assertThat(markdown)
        .contains("src/`orders`.java:10–12")
        .doesNotContain("`src/`orders`.java:10–12`");
  }

  @Test
  void atomicPublisherContractRejectsMixedV1AndV2FiveFilePublication() throws Exception {
    Method fileSet =
        Class.forName("org.sourceanalysis.app.artifact.AtomicCanonicalPublicationEngine")
            .getDeclaredMethod("businessProcessPublisherFiles", List.class);
    fileSet.setAccessible(true);
    List<ArtifactDescriptor> mixed =
        List.of(
            testDescriptor("business-processes.md", "repository-business-process-markdown-v1"),
            testDescriptor("process-coverage.json", "repository-business-process-coverage-v1"),
            testDescriptor(
                "repository-business-process-catalog.json",
                "repository-business-process-catalog-v1"),
            testDescriptor("source-refs.jsonl", "repository-business-process-source-references-v1"),
            testDescriptor(
                "sources.md", "repository-business-process-sources-markdown-v1"));

    assertThat(fileSet.invoke(null, mixed)).isNull();
  }

  @Test
  void reopensHistoricalInputsSeparatelyFromTheCurrentOutputPolicy() {
    ProcessDiscoveryResult discovered = discoveryResult();
    ArtifactControls historicalControls = CapturingModuleStore.controls('3');
    ArtifactControls currentControls = CapturingModuleStore.controls('4');
    CapturingModuleStore inputs =
        new CapturingModuleStore(
            discovered.activityCheckpoint(), discovered.materialCheckpoint(), historicalControls);
    CapturingModuleStore outputs = new CapturingModuleStore();

    BusinessProcessPublication published =
        new CanonicalBusinessProcessPublisher(inputs, outputs, currentControls).publish(discovered);

    assertThat(inputs.reopenCount()).isEqualTo(2);
    assertThat(outputs.reopenCount()).isZero();
    assertThat(outputs.installedControls()).isEqualTo(currentControls);
    assertThat(new BusinessProcessCheckpointReader(outputs).reopen(published.checkpoint()))
        .isEqualTo(published);
  }

  private static ProcessDiscoveryResult discoveryResult() {
    try {
      java.lang.reflect.Method activities =
          BusinessProcessDiscoveryTest.class.getDeclaredMethod("activities");
      java.lang.reflect.Method materials =
          BusinessProcessDiscoveryTest.class.getDeclaredMethod("materials");
      java.lang.reflect.Method profile =
          BusinessProcessDiscoveryTest.class.getDeclaredMethod("profile");
      activities.setAccessible(true);
      materials.setAccessible(true);
      profile.setAccessible(true);
      Class<?> providerType =
          Class.forName(
              "org.sourceanalysis.app.analysis.knowledge.BusinessProcessDiscoveryTest$ScriptedProvider");
      var constructor = providerType.getDeclaredConstructor();
      constructor.setAccessible(true);
      return new DefaultBusinessProcessDiscovery(
              (org.sourceanalysis.app.adapter.provider.StructuredModelProvider)
                  constructor.newInstance())
          .discover(
              new ProcessDiscoveryRequest(
                  (org.sourceanalysis.app.analysis.interpretation.activity
                          .ActivityExplanationResult)
                      activities.invoke(null),
                  (org.sourceanalysis.app.analysis.interpretation.material
                          .BusinessMaterialBuildResult)
                      materials.invoke(null),
                  (ProcessDiscoveryProfile) profile.invoke(null)));
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError(failure);
    }
  }

  private static JsonNode json(VerifiedCanonicalPayload payload) throws Exception {
    return new ObjectMapper().readTree(payload.canonicalUtf8().copyToByteArray());
  }

  private static ImmutableBytes canonical(JsonNode value) {
    return new CanonicalJsonCodec().encodeCanonical(value);
  }

  private static String utf8(VerifiedCanonicalPayload payload) {
    return new String(payload.canonicalUtf8().copyToByteArray(), StandardCharsets.UTF_8);
  }

  private static List<String> jsonStrings(JsonNode values) {
    List<String> result = new ArrayList<>();
    values.forEach(value -> result.add(value.asText()));
    return result;
  }

  private static JsonNode findByTextField(JsonNode values, String field, String expected) {
    for (JsonNode value : values) {
      if (expected.equals(value.path(field).asText())) {
        return value;
      }
    }
    throw new AssertionError("missing JSON value where " + field + "=" + expected);
  }

  private static String sourceMarkdown(BusinessProcessPublication publication) throws Exception {
    try {
      Method accessor = publication.getClass().getMethod("sourcesMarkdown");
      return (String) accessor.invoke(publication);
    } catch (NoSuchMethodException missing) {
      fail("BusinessProcessPublication must expose the reopened sources.md bytes", missing);
      return "";
    }
  }

  private static String rerenderSources(
      BusinessProcessCheckpointReader reader, BusinessProcessPublication publication)
      throws Exception {
    try {
      Method rerender =
          reader.getClass().getMethod("rerenderSources", BusinessProcessPublication.class);
      return (String) rerender.invoke(reader, publication);
    } catch (NoSuchMethodException missing) {
      fail("BusinessProcessCheckpointReader must rerender sources.md deterministically", missing);
      return "";
    }
  }

  private static ArtifactDescriptor testDescriptor(String fileName, String schemaVersion) {
    return new ArtifactDescriptor(
        fileName,
        "TEST_ARTIFACT",
        schemaVersion,
        ArtifactId.parse("test-artifact:" + "1".repeat(64)),
        fileName.endsWith(".md")
            ? org.sourceanalysis.app.artifact.CanonicalMediaType.TEXT_MARKDOWN
            : org.sourceanalysis.app.artifact.CanonicalMediaType.APPLICATION_JSON,
        0,
        Sha256Digest.parse("2".repeat(64)));
  }

  private static final class CapturingModuleStore implements CanonicalModuleArtifactStore {
    private final Map<ModulePublicationReference, ReopenedModulePublication> publications =
        new HashMap<>();
    private List<String> installedFileNames = List.of();
    private List<VerifiedCanonicalPayload> installedPayloads = List.of();
    private ModulePublicationReference installedReference;
    private String installedModuleVersion;
    private ArtifactControls installedControls;
    private int reopenCount;

    private CapturingModuleStore() {}

    private CapturingModuleStore(
        ModulePublicationReference activity, ModulePublicationReference material) {
      this(activity, material, controls('3'));
    }

    private CapturingModuleStore(
        ModulePublicationReference activity,
        ModulePublicationReference material,
        ArtifactControls controls) {
      publications.put(activity, upstream(activity, controls, "activity-upstream"));
      publications.put(material, upstream(material, controls, "material-upstream"));
    }

    @Override
    public InstalledModulePublication install(ModuleInstallRequest request) {
      List<VerifiedCanonicalPayload> payloads =
          request.payloads().stream().map(CapturingModuleStore::verified).toList();
      List<ArtifactDescriptor> descriptors =
          payloads.stream().map(VerifiedCanonicalPayload::descriptor).toList();
      installedFileNames = descriptors.stream().map(ArtifactDescriptor::fileName).toList();
      installedPayloads = List.copyOf(payloads);
      installedModuleVersion = request.moduleVersion();
      installedControls = request.controls();
      String digest = "9".repeat(64);
      ModulePublicationReference reference =
          new ModulePublicationReference(
              request.address(),
              ModuleArtifactRoot.parse("module-root:" + digest),
              ModuleReceiptId.parse("module-receipt:" + digest),
              Sha256Digest.parse(digest));
      installedReference = reference;
      ModuleReceipt receipt =
          new ModuleReceipt(
              "module-receipt-v2",
              reference.moduleReceiptId(),
              request.address(),
              request.moduleVersion(),
              request.upstreamArtifacts(),
              request.controls(),
              request.status(),
              descriptors,
              reference.moduleArtifactRoot(),
              request.gapRefs());
      publications.put(reference, new ReopenedModulePublication(reference, receipt, payloads));
      return new InstalledModulePublication(
          reference, ModuleInstallDisposition.INSTALLED, descriptors);
    }

    private void replaceInstalledPayload(String fileName, ImmutableBytes bytes) {
      List<VerifiedCanonicalPayload> replacement =
          installedPayloads.stream()
              .map(
                  payload -> {
                    if (!fileName.equals(payload.descriptor().fileName())) {
                      return payload;
                    }
                    CanonicalModulePayload rewritten =
                        new CanonicalModulePayload(
                            fileName,
                            payload.descriptor().artifactType(),
                            payload.descriptor().schemaVersion(),
                            payload.descriptor().artifactId(),
                            payload.descriptor().mediaType(),
                            bytes);
                    return verified(rewritten);
                  })
              .toList();
      if (replacement.stream()
          .map(payload -> payload.descriptor().fileName())
          .noneMatch(fileName::equals)) {
        throw new AssertionError("missing installed payload " + fileName);
      }
      installedPayloads = replacement;
      ReopenedModulePublication publication = publications.get(installedReference);
      publications.put(
          installedReference,
          new ReopenedModulePublication(
              publication.reference(), publication.receipt(), installedPayloads));
    }

    @Override
    public CanonicalArtifactPolicy resolveArtifactPolicy(ArtifactPolicyKey key) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ReopenedModulePublication reopen(ModulePublicationReference reference) {
      reopenCount++;
      ReopenedModulePublication reopened = publications.get(reference);
      if (reopened == null) {
        throw new IllegalArgumentException("unknown test publication");
      }
      return reopened;
    }

    private List<String> installedFileNames() {
      return installedFileNames;
    }

    private Map<String, VerifiedCanonicalPayload> installedPayloadsByFile() {
      Map<String, VerifiedCanonicalPayload> values = new HashMap<>();
      installedPayloads.forEach(payload -> values.put(payload.descriptor().fileName(), payload));
      return Map.copyOf(values);
    }

    private String installedModuleVersion() {
      return installedModuleVersion;
    }

    private ArtifactControls installedControls() {
      return installedControls;
    }

    private int reopenCount() {
      return reopenCount;
    }

    private static ReopenedModulePublication upstream(
        ModulePublicationReference reference, ArtifactControls controls, String prefix) {
      ArtifactDescriptor descriptor =
          new ArtifactDescriptor(
              prefix + ".json",
              "TEST_UPSTREAM",
              "test-v1",
              ArtifactId.parse(prefix + ":" + "1".repeat(64)),
              org.sourceanalysis.app.artifact.CanonicalMediaType.APPLICATION_JSON,
              2,
              Sha256Digest.parse("2".repeat(64)));
      ModuleReceipt receipt =
          new ModuleReceipt(
              "module-receipt-v2",
              reference.moduleReceiptId(),
              reference.address(),
              "v1",
              List.of(),
              controls,
              org.sourceanalysis.app.artifact.ModuleCompletionStatus.SUCCEEDED,
              List.of(descriptor),
              reference.moduleArtifactRoot(),
              List.of());
      return new ReopenedModulePublication(reference, receipt, List.of());
    }

    private static VerifiedCanonicalPayload verified(CanonicalModulePayload payload) {
      Sha256Digest sha = sha256(payload.canonicalUtf8());
      ArtifactDescriptor descriptor =
          new ArtifactDescriptor(
              payload.fileName(),
              payload.artifactType(),
              payload.schemaVersion(),
              payload.artifactId(),
              payload.mediaType(),
              payload.canonicalUtf8().size(),
              sha);
      return new VerifiedCanonicalPayload(descriptor, payload.canonicalUtf8());
    }

    private static ArtifactControls controls(char fill) {
      String digest = String.valueOf(fill).repeat(64);
      return new ArtifactControls(
          Sha256Digest.parse(digest),
          Sha256Digest.parse(digest),
          Sha256Digest.parse(digest),
          Sha256Digest.parse(digest),
          new ArtifactPolicyRegistryReference(
              ArtifactId.parse("artifact-policy-registry:" + digest), Sha256Digest.parse(digest)));
    }

    private static Sha256Digest sha256(ImmutableBytes bytes) {
      try {
        return Sha256Digest.parse(
            java.util.HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.copyToByteArray())));
      } catch (NoSuchAlgorithmException impossible) {
        throw new IllegalStateException(impossible);
      }
    }
  }
}
