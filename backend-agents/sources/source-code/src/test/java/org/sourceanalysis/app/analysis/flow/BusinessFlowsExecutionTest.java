package org.sourceanalysis.app.analysis.flow;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.code.CodeEngineException;
import org.sourceanalysis.app.analysis.code.EngineDescriptor;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.JavaCodeSession;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.fact.ProvenCodeFactsExecutor;
import org.sourceanalysis.app.analysis.fact.publish.ProvenCodeFactsReference;
import org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjection;
import org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjectionModulePublisher;
import org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjectionProfile;
import org.sourceanalysis.app.analysis.flow.capsule.EvidenceCapsuleProjector;
import org.sourceanalysis.app.analysis.flow.compiler.EntryContextAssembler;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilation;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilationModulePublisher;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilationProfile;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.flow.publish.FlowPublicationSpecifier;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsExecution;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsPublicFixture;
import org.sourceanalysis.app.artifact.AnalysisStepInstallRequest;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledAnalysisStepPublication;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/** Public-seam RED for executing the persisted Flow/Capsule/publication chain. */
class BusinessFlowsExecutionTest {

  @TempDir Path temporaryDirectory;

  @Test
  void executesFlowCompilationCapsuleProjectionAndFiveFilePublication() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.create(temporaryDirectory.resolve("two-entry-fixture"))) {
      BusinessFlowsReference result = execute(fixture);

      assertThat(fixture.stepArtifacts().reopen(result.publication()).semanticPayloads())
          .extracting(payload -> payload.descriptor().fileName())
          .containsExactly(
              "entry-dispositions.jsonl",
              "evidence-capsules.jsonl",
              "flow-coverage.json",
              "flow-gaps.jsonl",
              "flow-slices.json");
    }
  }

  @Test
  void publishesTheSameFiveFilesWhenTheEntryDenominatorHasNoFlows() throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createWithoutHttpEntries(
            temporaryDirectory.resolve("zero-entry-fixture"))) {
      BusinessFlowsReference result = execute(fixture);

      assertThat(fixture.stepArtifacts().reopen(result.publication()).semanticPayloads())
          .extracting(payload -> payload.descriptor().fileName())
          .containsExactly(
              "entry-dispositions.jsonl",
              "evidence-capsules.jsonl",
              "flow-coverage.json",
              "flow-gaps.jsonl",
              "flow-slices.json");
    }
  }

  @Test
  void publishesEveryPersistedJdtContextAsACapsuleWithoutInventingStrictFlowsOrProofs()
      throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createForJavaCodeIndex(
            temporaryDirectory.resolve("jdt-context-fixture"))) {
      String snapshotId = fixture.sourceReader().reopen(fixture.sourceInventory()).snapshotId();
      String controllerSource =
          new String(
              fixture.sourceReader().reopen(fixture.sourceInventory()).documents().stream()
                  .filter(
                      value ->
                          "src/main/java/com/example/OrderController.java".equals(value.path()))
                  .findFirst()
                  .orElseThrow()
                  .rawUtf8()
                  .copyToByteArray(),
              StandardCharsets.UTF_8);
      ProgramGraphsExecution graphExecution =
          new ProgramGraphsExecution(
              fixture.sourceReader(), fixture.moduleArtifacts(), fixture.stepArtifacts());
      var graphs =
          graphExecution.execute(
              fixture.sourceInventory(),
              fixture.applicationDiscovery(),
              fakeJdtSession(snapshotId, controllerSource),
              fixture.artifactControls());
      ProvenCodeFactsReference facts =
          new ProvenCodeFactsExecutor(
                  fixture.sourceReader(), fixture.moduleArtifacts(), fixture.stepArtifacts())
              .execute(fixture.sourceInventory(), fixture.applicationDiscovery(), graphs);

      FlowCompilation compilation =
          new EntryContextAssembler(fixture.stepArtifacts())
              .assemble(fixture.applicationDiscovery(), graphs, facts, flowProfile());
      ModulePublicationReference compilationPublication =
          new FlowCompilationModulePublisher(fixture.moduleArtifacts(), fixture.stepArtifacts())
              .publish(fixture.applicationDiscovery(), graphs, facts, compilation);
      CountingAnalysisStepArtifactStore counting =
          new CountingAnalysisStepArtifactStore(fixture.stepArtifacts());
      CapsuleProjection projection =
          new EvidenceCapsuleProjector(fixture.moduleArtifacts(), counting, fixture.sourceReader())
              .project(
                  compilationPublication,
                  fixture.sourceInventory(),
                  graphs,
                  facts,
                  capsuleProfile());
      assertThat(counting.programGraphReopenCount()).isEqualTo(1);
      ModulePublicationReference capsulePublication =
          new CapsuleProjectionModulePublisher(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .publish(
                  compilationPublication, fixture.sourceInventory(), graphs, facts, projection);
      BusinessFlowsReference result =
          new FlowPublicationSpecifier(
                  fixture.moduleArtifacts(), fixture.stepArtifacts(), fixture.sourceReader())
              .specify(
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  graphs,
                  facts,
                  compilationPublication,
                  capsulePublication);

      JsonNode persistedCompilation =
          new CanonicalJsonCodec()
              .parseCanonical(
                  fixture
                      .moduleArtifacts()
                      .reopen(compilationPublication)
                      .payloads()
                      .get(0)
                      .canonicalUtf8());
      assertThat(persistedCompilation.path("schemaVersion").asText())
          .isEqualTo("business-flows-flow-compilation-v6");
      assertReferenceOnlyEntryContexts(
          persistedCompilation.path("payload").path("entryContexts"),
          fixture.stepArtifacts().reopen(graphs.publication()).semanticPayloads().get(0));

      JsonNode persistedCapsules =
          new CanonicalJsonCodec()
              .parseCanonical(
                  fixture
                      .moduleArtifacts()
                      .reopen(capsulePublication)
                      .payloads()
                      .get(0)
                      .canonicalUtf8());
      assertThat(persistedCapsules.path("schemaVersion").asText())
          .isEqualTo("business-flows-capsule-projection-v11");
      assertReferenceOnlyCapsules(
          persistedCapsules.path("payload").path("capsules"),
          fixture.moduleArtifacts().reopen(compilationPublication).payloads().get(0));

      var reopened = fixture.stepArtifacts().reopen(result.publication());
      var payloads =
          reopened.semanticPayloads().stream()
              .collect(
                  java.util.stream.Collectors.toMap(
                      value -> value.descriptor().fileName(), value -> value));
      assertThat(payloads)
          .containsOnlyKeys(
              "entry-dispositions.jsonl",
              "evidence-capsules.jsonl",
              "flow-coverage.json",
              "flow-gaps.jsonl",
              "flow-slices.json");
      JsonNode flows =
          new CanonicalJsonCodec().parseCanonical(payloads.get("flow-slices.json").canonicalUtf8());
      assertThat(flows.path("schemaVersion").textValue())
          .isEqualTo("business-flows-flow-slices-v6");
      assertThat(flows.path("flowSlices")).isEmpty();
      assertThat(flows.path("entryContexts")).hasSize(2);
      assertReferenceOnlyEntryContexts(
          flows.path("entryContexts"),
          fixture.stepArtifacts().reopen(graphs.publication()).semanticPayloads().get(0));
      assertThat(flows.path("entryContexts"))
          .allSatisfy(
              context -> {
                assertThat(context.path("flowSliceId").isNull()).isTrue();
                assertThat(context.path("limitations"))
                    .as("repeated unresolved calls must not invalidate the persisted entry context")
                    .hasSize(1);
              });
      List<JsonNode> dispositions =
          jsonLines(payloads.get("entry-dispositions.jsonl").canonicalUtf8());
      assertThat(dispositions).hasSize(2);
      assertThat(dispositions)
          .allSatisfy(
              disposition -> {
                assertThat(disposition.path("schemaVersion").textValue())
                    .isEqualTo("business-flows-entry-disposition-v2");
                assertThat(disposition.path("disposition").textValue()).isEqualTo("CONTEXT_ONLY");
              });
      JsonNode coverage =
          new CanonicalJsonCodec()
              .parseCanonical(payloads.get("flow-coverage.json").canonicalUtf8());
      assertThat(coverage.path("schemaVersion").textValue())
          .isEqualTo("business-flows-flow-coverage-v2");
      assertThat(coverage.path("contextOnlyEntryIds")).hasSize(2);
      assertThat(coverage.path("modelEligibleEntryIds")).hasSize(2);

      List<JsonNode> capsules = jsonLines(payloads.get("evidence-capsules.jsonl").canonicalUtf8());
      assertThat(capsules).hasSize(2);
      assertReferenceOnlyCapsules(
          capsules, fixture.moduleArtifacts().reopen(compilationPublication).payloads().get(0));
      assertThat(capsules)
          .allSatisfy(
              capsule -> {
                assertThat(capsule.path("schemaVersion").textValue())
                    .isEqualTo("business-flows-evidence-capsule-v9");
                assertThat(capsule.path("flowSliceId").isNull()).isTrue();
                assertThat(capsule.path("proofPackId").isNull()).isTrue();
              });
    }
  }

  private static final class CountingAnalysisStepArtifactStore
      implements CanonicalAnalysisStepArtifactStore {
    private final CanonicalAnalysisStepArtifactStore delegate;
    private int programGraphReopenCount;

    private CountingAnalysisStepArtifactStore(CanonicalAnalysisStepArtifactStore delegate) {
      this.delegate = delegate;
    }

    @Override
    public InstalledAnalysisStepPublication install(AnalysisStepInstallRequest request) {
      return delegate.install(request);
    }

    @Override
    public ReopenedAnalysisStepPublication reopen(AnalysisStepPublicationReference reference) {
      if (reference.address().analysisStepKey() == AnalysisStepKey.PROGRAM_GRAPHS) {
        programGraphReopenCount++;
      }
      return delegate.reopen(reference);
    }

    private int programGraphReopenCount() {
      return programGraphReopenCount;
    }
  }

  @Test
  void preservesAnUncollectedJdtEntryReasonWithoutDroppingCollectedSiblingContext()
      throws Exception {
    try (ProgramGraphsPublicFixture fixture =
        ProgramGraphsPublicFixture.createForJavaCodeIndex(
            temporaryDirectory.resolve("jdt-partial-context-fixture"))) {
      var source = fixture.sourceReader().reopen(fixture.sourceInventory());
      String controllerSource =
          new String(
              source.documents().stream()
                  .filter(
                      value ->
                          "src/main/java/com/example/OrderController.java".equals(value.path()))
                  .findFirst()
                  .orElseThrow()
                  .rawUtf8()
                  .copyToByteArray(),
              StandardCharsets.UTF_8);
      JavaCodeSession complete = fakeJdtSession(source.snapshotId(), controllerSource);
      java.util.concurrent.atomic.AtomicInteger calls =
          new java.util.concurrent.atomic.AtomicInteger();
      JavaCodeSession partial =
          new JavaCodeSession() {
            @Override
            public JavaDeclarationCatalog catalog() {
              return complete.catalog();
            }

            @Override
            public EntryCodeContext collect(EntrySeed entry) {
              if (calls.incrementAndGet() == 2) {
                throw new CodeEngineException(
                    CodeEngineException.JDT_QUERY_FAILED, "named fixture navigation failure");
              }
              return complete.collect(entry);
            }

            @Override
            public EngineDescriptor descriptor() {
              return complete.descriptor();
            }

            @Override
            public void close() {}
          };
      var graphs =
          new ProgramGraphsExecution(
                  fixture.sourceReader(), fixture.moduleArtifacts(), fixture.stepArtifacts())
              .execute(
                  fixture.sourceInventory(),
                  fixture.applicationDiscovery(),
                  partial,
                  fixture.artifactControls());
      var facts =
          new ProvenCodeFactsExecutor(
                  fixture.sourceReader(), fixture.moduleArtifacts(), fixture.stepArtifacts())
              .execute(fixture.sourceInventory(), fixture.applicationDiscovery(), graphs);
      BusinessFlowsReference result =
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

      var payloads =
          fixture.stepArtifacts().reopen(result.publication()).semanticPayloads().stream()
              .collect(
                  java.util.stream.Collectors.toMap(
                      value -> value.descriptor().fileName(), value -> value));
      List<JsonNode> dispositions =
          jsonLines(payloads.get("entry-dispositions.jsonl").canonicalUtf8());
      assertThat(dispositions)
          .extracting(value -> value.path("disposition").textValue())
          .containsExactly("CONTEXT_ONLY", "GAP");
      assertThat(jsonLines(payloads.get("evidence-capsules.jsonl").canonicalUtf8())).hasSize(1);
      JsonNode flowSlices =
          new CanonicalJsonCodec().parseCanonical(payloads.get("flow-slices.json").canonicalUtf8());
      assertThat(flowSlices.path("schemaVersion").asText())
          .isEqualTo("business-flows-flow-slices-v6");
      assertThat(flowSlices.path("entryContexts"))
          .anySatisfy(
              context -> {
                assertThat(context.path("collectionStatus").asText()).isEqualTo("COLLECTED");
                assertThat(context.has("codeContext")).isFalse();
                assertThat(context.path("codeContextRef").isObject()).isTrue();
              })
          .anySatisfy(
              context -> {
                assertThat(context.path("collectionStatus").asText()).isEqualTo("NOT_COLLECTED");
                assertThat(context.has("codeContext")).isFalse();
                assertThat(context.path("codeContextRef").isNull()).isTrue();
                assertThat(context.path("collectionReason").asText()).isNotBlank();
              });
      assertThat(jsonLines(payloads.get("flow-gaps.jsonl").canonicalUtf8()))
          .singleElement()
          .satisfies(
              gap -> assertThat(gap.path("reasonCode").textValue()).contains("JDT_QUERY_FAILED"));
    }
  }

  private static BusinessFlowsReference execute(ProgramGraphsPublicFixture fixture)
      throws Exception {
    ProvenCodeFactsReference facts =
        new ProvenCodeFactsExecutor(
                fixture.sourceReader(), fixture.moduleArtifacts(), fixture.stepArtifacts())
            .execute(
                fixture.sourceInventory(), fixture.applicationDiscovery(), fixture.programGraphs());
    Class<?> requestType =
        typeOrNull("org.sourceanalysis.app.analysis.flow.BusinessFlowsExecutionRequest");
    Class<?> executorType =
        typeOrNull("org.sourceanalysis.app.analysis.flow.BusinessFlowsExecutor");

    assertThat(requestType)
        .as("Step 05 needs one typed execution request rather than six unbound caller arguments")
        .isNotNull();
    assertThat(executorType)
        .as("Step 05 needs one production execution seam instead of test-only M1–M3 composition")
        .isNotNull();
    assertThat(executorType.getDeclaredConstructors())
        .as("the execution seam cannot accept caller-owned filesystem paths")
        .allSatisfy(
            constructor -> assertThat(constructor.getParameterTypes()).doesNotContain(Path.class));

    Object request =
        requestType
            .getConstructor(
                org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference.class,
                org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference.class,
                org.sourceanalysis.app.analysis.graph.ProgramGraphsReference.class,
                ProvenCodeFactsReference.class,
                FlowCompilationProfile.class,
                CapsuleProjectionProfile.class)
            .newInstance(
                fixture.sourceInventory(),
                fixture.applicationDiscovery(),
                fixture.programGraphs(),
                facts,
                flowProfile(),
                capsuleProfile());
    Object executor =
        executorType
            .getConstructor(
                org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader.class,
                CanonicalModuleArtifactStore.class,
                CanonicalAnalysisStepArtifactStore.class)
            .newInstance(
                fixture.sourceReader(), fixture.moduleArtifacts(), fixture.stepArtifacts());
    Method execute = executorType.getMethod("execute", requestType);
    return (BusinessFlowsReference) execute.invoke(executor, request);
  }

  private static FlowCompilationProfile flowProfile() {
    return new FlowCompilationProfile(
        reference("flow-profile", 'a', 'b'), 16, 8, 64, 96, 32, 64, 256);
  }

  private static void assertReferenceOnlyEntryContexts(
      JsonNode contexts, VerifiedCanonicalPayload indexPayload) {
    assertThat(contexts).hasSize(2);
    assertThat(contexts)
        .allSatisfy(
            context -> {
              assertThat(context.path("collectionStatus").textValue()).isEqualTo("COLLECTED");
              assertThat(context.has("codeContext")).isFalse();
              JsonNode reference = context.path("codeContextRef");
              assertThat(reference.isObject()).isTrue();
              assertThat(reference.path("entryId").textValue())
                  .isEqualTo(context.path("entryId").textValue());
              assertThat(reference.path("indexArtifact").path("artifactId").textValue())
                  .isEqualTo(indexPayload.descriptor().artifactId().value());
              assertThat(reference.path("indexArtifact").path("sha256").textValue())
                  .isEqualTo(indexPayload.descriptor().sha256().value());
            });
  }

  private static void assertReferenceOnlyCapsules(
      JsonNode capsules, VerifiedCanonicalPayload compilationPayload) {
    assertThat(capsules).hasSize(2);
    capsules.forEach(capsule -> assertReferenceOnlyCapsule(capsule, compilationPayload));
  }

  private static void assertReferenceOnlyCapsules(
      List<JsonNode> capsules, VerifiedCanonicalPayload compilationPayload) {
    assertThat(capsules).hasSize(2);
    capsules.forEach(capsule -> assertReferenceOnlyCapsule(capsule, compilationPayload));
  }

  private static void assertReferenceOnlyCapsule(
      JsonNode capsule, VerifiedCanonicalPayload compilationPayload) {
    assertThat(capsule.has("entryContext")).isFalse();
    JsonNode reference = capsule.path("entryContextRef");
    assertThat(reference.isObject()).isTrue();
    assertThat(reference.path("entryContextId").asText()).isNotBlank();
    assertThat(reference.path("compilationArtifact").path("artifactId").asText())
        .isEqualTo(compilationPayload.descriptor().artifactId().value());
    assertThat(reference.path("compilationArtifact").path("sha256").asText())
        .isEqualTo(compilationPayload.descriptor().sha256().value());
  }

  private static CapsuleProjectionProfile capsuleProfile() {
    return new CapsuleProjectionProfile(
        reference("capsule-profile", 'c', 'd'), 16, 32, 4_096, 24_576);
  }

  private static ArtifactReference reference(String prefix, char identity, char content) {
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + String.valueOf(identity).repeat(64)),
        Sha256Digest.parse(String.valueOf(content).repeat(64)));
  }

  private static JavaCodeSession fakeJdtSession(String snapshotId, String controllerSource) {
    EntryCodeContext.TechnicalEnhancements enhancements =
        new EntryCodeContext.TechnicalEnhancements(
            EntryCodeContext.Availability.NOT_PRODUCED,
            "STRICT_GRAPH_ENRICHMENT_NOT_REQUESTED_BY_TEST_JDT",
            List.of(),
            List.of(),
            null);
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
        int start = entry.methodRange().startOffsetUtf16();
        int end = Math.addExact(start, entry.methodRange().lengthUtf16());
        EntryCodeContext.SourceSource source =
            new EntryCodeContext.SourceSource(
                "src/main/java/com/example/OrderController.java",
                entry.methodRange(),
                controllerSource.substring(start, end));
        EntryCodeContext.MethodCode method =
            new EntryCodeContext.MethodCode(
                entry.methodKey(),
                "METHOD",
                "com.example.OrderController",
                "entry",
                List.of(),
                "Object",
                source,
                true);
        return new EntryCodeContext(
            EntryCodeContext.SCHEMA_VERSION,
            entry.entryId(),
            entry.methodKey(),
            List.of(method),
            List.of(),
            List.of(),
            List.of(
                new EntryCodeContext.Limitation(
                    "UNRESOLVED_CALL", "external boundary", List.of(entry.methodKey()), List.of()),
                new EntryCodeContext.Limitation(
                    "UNRESOLVED_CALL", "external boundary", List.of(entry.methodKey()), List.of())),
            enhancements);
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

  private static List<JsonNode> jsonLines(ImmutableBytes bytes) {
    CanonicalJsonCodec codec = new CanonicalJsonCodec();
    String text = new String(bytes.copyToByteArray(), StandardCharsets.UTF_8);
    return text.isEmpty()
        ? List.of()
        : text.substring(0, text.length() - 1)
            .lines()
            .map(
                line ->
                    codec.parseCanonical(
                        ImmutableBytes.copyOf(line.getBytes(StandardCharsets.UTF_8))))
            .toList();
  }

  private static Class<?> typeOrNull(String name) {
    try {
      return Class.forName(name);
    } catch (ClassNotFoundException missing) {
      return null;
    }
  }
}
