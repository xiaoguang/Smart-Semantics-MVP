package org.sourceanalysis.app.analysis.interpretation.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelProviderFailure;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.interpretation.material.LegacyM10CheckpointFixture;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialProfile;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepInstallRequest;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepReceipt;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactDescriptor;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledAnalysisStepPublication;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;
import org.sourceanalysis.app.runtime.modeljob.ModelJobExecutionConfiguration;
import org.sourceanalysis.app.runtime.modeljob.ModelJobProviderBinding;
import org.sourceanalysis.app.runtime.modeljob.PrivateModelJobResultStore;

/** RED contract: the formal Step05 request routes oversized packets through bounded reading. */
class ActivityLargePacketFormalEntryTest {

  private static final ModelRuntimeIdentityV1 IDENTITY =
      new ModelRuntimeIdentityV1("scripted", "formal-large-packet", "high", "read-only");
  private static final ActivityExplanationProfile PROFILE =
      new ActivityExplanationProfile(20_000, 8_000, 4, 32, 2_000);
  private static final String M2_CALL_EXPRESSION = "validate()";

  @Test
  void selectedSampleKeepsTheProviderBindingFromItsFullPacketOrdinal(@TempDir Path journal) {
    CodeReadingMaterialSet original = largeStep05Material();
    CodeReadingMaterialSet.Packet first = original.packets().get(0);
    EntrySeed entry = first.entries().get(0);
    String secondId = first.packetId() + "-z";
    CodeReadingMaterialSet.Packet second =
        new CodeReadingMaterialSet.Packet(
            secondId,
            List.of(
                new EntrySeed(
                    "entry:second", entry.methodKey(), entry.methodRange(), entry.trigger())),
            first.methods(),
            List.of(),
            first.persistence(),
            first.sourceReferences(),
            List.of(),
            first.limitations(),
            first.selfContainedUtf8Bytes());
    CodeReadingMaterialSet materials =
        new CodeReadingMaterialSet(
            original.header(),
            List.of(first, second),
            List.of(
                original.coverage().get(0),
                new CodeReadingMaterialSet.EntryCoverage(
                    "entry:second",
                    List.of(secondId),
                    CodeReadingMaterialSet.CoverageStatus.COLLECTED,
                    List.of())));
    FormalLargePacketProvider firstProvider = new FormalLargePacketProvider();
    FormalLargePacketProvider secondProvider = new FormalLargePacketProvider();
    ModelJobExecutionConfiguration execution =
        new ModelJobExecutionConfiguration(
            1,
            Map.of(
                "first",
                new ModelJobProviderBinding("first", "scope-first", 1, firstProvider, IDENTITY),
                "second",
                new ModelJobProviderBinding("second", "scope-second", 1, secondProvider, IDENTITY)),
            Map.of(
                "activity", List.of("first", "second"),
                "processGroup", List.of("first"),
                "repositorySummary", List.of("first"),
                "report", List.of("first")),
            journal,
            AnalysisRunId.parse("analysis-run:" + "9".repeat(64)));

    ActivityExplainer.forExecution(execution)
        .explain(new ExplainCodeReadingMaterialsRequest(materials, PROFILE, 1, Set.of(secondId)));

    assertThat(firstProvider.taskKinds()).isEmpty();
    assertThat(secondProvider.taskKinds()).isNotEmpty();
  }

  @Test
  void completeOversizedPacketCanBeReusedFromPriorBatchWithoutReadingOrModelCalls(
      @TempDir Path journal) {
    CodeReadingMaterialSet materials = largeStep05Material();
    FormalLargePacketProvider firstProvider = new FormalLargePacketProvider();
    AnalysisRunId firstRun = AnalysisRunId.parse("analysis-run:" + "7".repeat(64));
    ActivityExplanationResult first =
        ActivityExplainer.forExecution(execution(journal, firstRun, null, firstProvider))
            .explain(new ExplainCodeReadingMaterialsRequest(materials, PROFILE, 1));
    assertThat(first.reviewedActivities()).hasSize(2);

    FormalLargePacketProvider secondProvider = new FormalLargePacketProvider();
    AnalysisRunId secondRun = AnalysisRunId.parse("analysis-run:" + "8".repeat(64));
    ActivityExplanationResult reused =
        ActivityExplainer.forExecution(execution(journal, secondRun, firstRun, secondProvider))
            .explain(new ExplainCodeReadingMaterialsRequest(materials, PROFILE, 1));

    assertThat(secondProvider.taskKinds()).isEmpty();
    assertThat(reused.reviewedActivities()).containsExactlyElementsOf(first.reviewedActivities());
    assertThat(reused.coverage()).containsExactlyElementsOf(first.coverage());
  }

  @Test
  void aSavedReadingPlanWithNoActivityDoesNotSuppressExplicitRetry(@TempDir Path journal) {
    CodeReadingMaterialSet materials = largeStep05Material();
    AnalysisRunId incompleteRun = AnalysisRunId.parse("analysis-run:" + "3".repeat(64));
    StructuredModelProvider noSelection =
        request ->
            new StructuredModelResponse(
                ImmutableBytes.copyOf(
                    "{\"requestedNavigationPages\":[],\"requestedUnitKeys\":[],\"slices\":[],\"unknowns\":[]}"
                        .getBytes(StandardCharsets.UTF_8)),
                IDENTITY);
    ActivityExplanationResult incomplete =
        ActivityExplainer.forExecution(execution(journal, incompleteRun, null, noSelection))
            .explain(new ExplainCodeReadingMaterialsRequest(materials, PROFILE, 1));
    assertThat(incomplete.reviewedActivities()).isEmpty();
    assertThat(incomplete.coverage())
        .singleElement()
        .extracting("disposition")
        .isEqualTo("NOT_ANALYZED");

    FormalLargePacketProvider retryProvider = new FormalLargePacketProvider();
    AnalysisRunId retryRun = AnalysisRunId.parse("analysis-run:" + "4".repeat(64));
    ActivityExplanationResult retried =
        ActivityExplainer.forExecution(execution(journal, retryRun, incompleteRun, retryProvider))
            .explain(new ExplainCodeReadingMaterialsRequest(materials, PROFILE, 1));

    assertThat(retryProvider.taskKinds()).contains("ACTIVITY_READING_PLAN", "ACTIVITY_DRAFT");
    assertThat(retried.reviewedActivities()).hasSize(2);
  }

  @Test
  void failedReadingPlanRecordsItsActualFailedStage(@TempDir Path journal) {
    AnalysisRunId run = AnalysisRunId.parse("analysis-run:" + "6".repeat(64));
    StructuredModelProvider failing =
        request -> {
          throw new StructuredModelProviderFailure("MODEL_PROVIDER_TIMEOUT", true, true);
        };

    ActivityExplanationResult result =
        ActivityExplainer.forExecution(execution(journal, run, null, failing))
            .explain(new ExplainCodeReadingMaterialsRequest(largeStep05Material(), PROFILE, 1));

    assertThat(result.coverage())
        .singleElement()
        .satisfies(entry -> assertThat(entry.disposition()).isEqualTo("NOT_ANALYZED"));
    assertThat(new PrivateModelJobResultStore(journal, run, "activity").listTerminalFailures())
        .singleElement()
        .satisfies(
            record -> {
              assertThat(record.path("stageKey").asText()).isEqualTo("READING_PLAN");
              assertThat(record.path("attemptsUsed").asInt()).isEqualTo(1);
              assertThat(record.path("maxAttempts").asInt()).isEqualTo(3);
            });
  }

  private static ModelJobExecutionConfiguration execution(
      Path journal, AnalysisRunId run, AnalysisRunId reuseFrom, StructuredModelProvider provider) {
    return new ModelJobExecutionConfiguration(
        1,
        Map.of("pro", new ModelJobProviderBinding("pro", "pro-account", 1, provider, IDENTITY)),
        Map.of(
            "activity", List.of("pro"),
            "processGroup", List.of("pro"),
            "repositorySummary", List.of("pro"),
            "report", List.of("pro")),
        journal,
        run,
        reuseFrom);
  }

  @Test
  void exactPacketSelectionRejectsAnIncompleteMultiPacketEntry() {
    CodeReadingMaterialSet original = largeStep05Material();
    CodeReadingMaterialSet.Packet first = original.packets().get(0);
    CodeReadingMaterialSet.Packet second =
        new CodeReadingMaterialSet.Packet(
            first.packetId() + "-part-two",
            first.entries(),
            first.methods(),
            first.calls(),
            first.persistence(),
            first.sourceReferences(),
            first.unselectedUnits(),
            first.limitations(),
            first.selfContainedUtf8Bytes());
    CodeReadingMaterialSet material =
        new CodeReadingMaterialSet(
            original.header(),
            List.of(first, second),
            List.of(
                new CodeReadingMaterialSet.EntryCoverage(
                    "entry:large",
                    List.of(first.packetId(), second.packetId()),
                    CodeReadingMaterialSet.CoverageStatus.COLLECTED,
                    List.of())));

    assertThatThrownBy(
            () ->
                new ExplainCodeReadingMaterialsRequest(
                    material, PROFILE, 1, Set.of(first.packetId())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("complete entry");
  }

  @Test
  void formalStep05EntryReadsOversizedPacketAsTwoCompleteScopesInsteadOfBudgetRejectingIt() {
    FormalLargePacketProvider provider = new FormalLargePacketProvider();

    ActivityExplanationResult result =
        new ActivityExplainer(provider)
            .explain(new ExplainCodeReadingMaterialsRequest(largeStep05Material(), PROFILE, 1));

    List<String> taskKinds = provider.taskKinds();
    long readingPlanCalls = taskKinds.stream().filter("ACTIVITY_READING_PLAN"::equals).count();
    assertThat(readingPlanCalls)
        .as("the formal request must have bounded reading before local Activity stages")
        .isBetween(1L, 132L);
    assertThat(taskKinds.subList(Math.toIntExact(readingPlanCalls), taskKinds.size()))
        .as("the final model stages are exactly two independent DRAFT-to-REVIEW scopes")
        .containsExactly("ACTIVITY_DRAFT", "ACTIVITY_REVIEW", "ACTIVITY_DRAFT", "ACTIVITY_REVIEW");
    assertThat(provider.draftInputs()).hasSize(2);
    assertThat(provider.reviewInputs()).hasSize(2);
    assertThat(provider.draftInputs())
        .anySatisfy(
            draft ->
                assertThat(scalarText(draft))
                    .as("the M2 scope receives its saved entry-to-M2 call expression")
                    .contains(M2_CALL_EXPRESSION));
    assertThat(result.reviewedActivities()).hasSize(2);
    assertThat(result.reviewedActivities())
        .extracting(ReviewedActivity::sliceKey)
        .containsExactlyInAnyOrder("slice-m2", "slice-m3");
    assertThat(result.reviewedActivities())
        .allSatisfy(
            activity -> {
              assertThat(activity.materialSource()).isEqualTo("CODE_READING_MATERIALS");
              assertThat(activity.originalSourceRefs()).containsEntry("S1", "S1");
            });
    assertThat(result.coverage())
        .singleElement()
        .satisfies(
            coverage -> {
              assertThat(coverage.entryId()).isEqualTo("entry:large");
              assertThat(coverage.disposition()).isEqualTo("ANALYZED_WITH_GAPS");
              assertThat(coverage.reasonCode()).isNull();
            });
    assertThat(result.coverage())
        .noneSatisfy(
            coverage -> assertThat(coverage.reasonCode()).isEqualTo("NOT_ANALYZED_BUDGET"));

    CodeReadingMaterialSet material = largeStep05Material();
    AnalysisStepPublicationReference source =
        publication(
            material.header().sourceInventory().publication().address().runId(),
            AnalysisStepKey.BUSINESS_FLOWS,
            '4');
    LegacyM10CheckpointFixture.HistoricalCheckpoint output =
        LegacyM10CheckpointFixture.openWritable();
    ModulePublicationReference checkpoint =
        new ActivityExplanationCheckpointPublisher(output.artifacts())
            .publishStep05(
                AnalysisRunId.parse("analysis-run:" + "a".repeat(64)),
                source,
                stepStore(source),
                new ArtifactControls(
                    new Sha256Digest("1".repeat(64)),
                    new Sha256Digest("2".repeat(64)),
                    new Sha256Digest("3".repeat(64)),
                    new Sha256Digest("4".repeat(64)),
                    new ArtifactPolicyRegistryReference(
                        ArtifactId.parse("artifact-policy-registry:" + "5".repeat(64)),
                        new Sha256Digest("5".repeat(64)))),
                material,
                result.reviewedActivities(),
                result.coverage(),
                result.unexplainedActivityEntries());
    ActivityExplanationResult reopened =
        new ActivityExplanationCheckpointReader(output.artifacts()).reopen(checkpoint);
    assertThat(reopened.reviewedActivities())
        .containsExactlyInAnyOrderElementsOf(result.reviewedActivities());
    assertThat(reopened.coverage()).isEqualTo(result.coverage());
  }

  @Test
  void formalStep05EntryUsesBoundedReadingWhenOnlyTheDraftWouldFit() {
    CodeReadingMaterialSet materials = largeStep05Material();
    CodeReadingMaterialSet.Packet packet = materials.packets().get(0);
    int fullDraftBytes =
        new ActivityMaterialProjector()
            .materialize(new ActivityMaterialProjector().project(packet, PROFILE))
            .modelInputJson()
            .size();
    ActivityExplanationProfile nearReviewLimit =
        new ActivityExplanationProfile(fullDraftBytes + 100, 8_000, 4, 32, 2_000);
    FormalLargePacketProvider provider = new FormalLargePacketProvider();

    ActivityExplanationResult result =
        new ActivityExplainer(provider)
            .explain(new ExplainCodeReadingMaterialsRequest(materials, nearReviewLimit, 1));

    assertThat(provider.taskKinds()).startsWith("ACTIVITY_READING_PLAN");
    assertThat(result.reviewedActivities()).hasSize(2);
  }

  private static CanonicalAnalysisStepArtifactStore stepStore(
      AnalysisStepPublicationReference reference) {
    ImmutableBytes bytes = ImmutableBytes.copyOf("{}\n".getBytes(StandardCharsets.UTF_8));
    ArtifactDescriptor descriptor =
        new ArtifactDescriptor(
            "code-reading-materials.jsonl",
            "BUSINESS_FLOWS_CODE_READING_MATERIALS",
            "business-flows-code-reading-materials-v1",
            ArtifactId.parse("code-reading-materials:" + "a".repeat(64)),
            CanonicalMediaType.APPLICATION_X_NDJSON,
            bytes.size(),
            new Sha256Digest("a".repeat(64)));
    AnalysisStepReceipt receipt =
        new AnalysisStepReceipt(
            "analysis-step-receipt-v1",
            reference.analysisStepReceiptId(),
            reference.address(),
            null,
            List.of(),
            null,
            org.sourceanalysis.app.artifact.ModuleCompletionStatus.SUCCEEDED,
            List.of(descriptor),
            null,
            reference.analysisStepArtifactRoot(),
            List.of());
    return new CanonicalAnalysisStepArtifactStore() {
      @Override
      public InstalledAnalysisStepPublication install(AnalysisStepInstallRequest request) {
        throw new AssertionError("publishing Activity must not re-run Step05");
      }

      @Override
      public ReopenedAnalysisStepPublication reopen(AnalysisStepPublicationReference requested) {
        assertThat(requested).isEqualTo(reference);
        return new ReopenedAnalysisStepPublication(
            reference, receipt, List.of(new VerifiedCanonicalPayload(descriptor, bytes)), null);
      }
    };
  }

  private static CodeReadingMaterialSet largeStep05Material() {
    ActivityMaterialView view = ActivityReadingCoordinatorTest.largeView();
    CodeReadingMaterialSet.Packet original = view.packet();
    String entrySource = ActivityReadingCoordinatorTest.ENTRY_BODY;
    EntryCodeContext.CallSite entryToM2 =
        new EntryCodeContext.CallSite(
            "call:large-entry-to-m2",
            "method:entry",
            "METHOD",
            new SourceRange(0, entrySource.length(), 1, 1),
            new SourceRange(0, entrySource.length(), 1, 1),
            M2_CALL_EXPRESSION,
            List.of(
                new EntryCodeContext.CallTarget(
                    "method:two",
                    List.of("DECLARATION"),
                    "example.large.LargeService#validate",
                    List.of("CALL_HIERARCHY"),
                    "BODY_INCLUDED",
                    null,
                    List.of())));
    CodeReadingMaterialSet.Packet packet =
        new CodeReadingMaterialSet.Packet(
            original.packetId(),
            original.entries(),
            original.methods(),
            List.of(new CodeReadingMaterialSet.EntryCall("entry:large", entryToM2)),
            original.persistence(),
            original.sourceReferences(),
            original.unselectedUnits(),
            original.limitations(),
            original.selfContainedUtf8Bytes());
    AnalysisRunId run = AnalysisRunId.parse("analysis-run:" + "f".repeat(64));
    CodeReadingMaterialSet.Header header =
        new CodeReadingMaterialSet.Header(
            new VerifiedSourceInventoryReference(
                publication(run, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, '1')),
            new ProgramGraphsReference(publication(run, AnalysisStepKey.PROGRAM_GRAPHS, '2')),
            publication(run, AnalysisStepKey.PROVEN_CODE_FACTS, '3'),
            "snapshot:" + "f".repeat(64),
            new CodeReadingMaterialProfile(128_000L, 8));
    return new CodeReadingMaterialSet(
        header,
        List.of(packet),
        List.of(
            new CodeReadingMaterialSet.EntryCoverage(
                "entry:large",
                List.of(packet.packetId()),
                CodeReadingMaterialSet.CoverageStatus.COLLECTED,
                List.of())));
  }

  private static AnalysisStepPublicationReference publication(
      AnalysisRunId run, AnalysisStepKey key, char fill) {
    String digest = String.valueOf(fill).repeat(64);
    return new AnalysisStepPublicationReference(
        new AnalysisStepPublicationAddress(run, key),
        new AnalysisStepArtifactRoot("analysis-step-root:" + digest),
        new AnalysisStepReceiptId("analysis-step-receipt:" + digest),
        new Sha256Digest(digest));
  }

  private static List<String> scalarText(JsonNode value) {
    List<String> values = new ArrayList<>();
    collectText(value, values);
    return values;
  }

  private static void collectText(JsonNode value, List<String> values) {
    if (value.isTextual()) {
      values.add(value.textValue());
    }
    value.elements().forEachRemaining(child -> collectText(child, values));
  }

  private static final class FormalLargePacketProvider implements StructuredModelProvider {
    private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    private final List<StructuredModelRequest> requests = new ArrayList<>();
    private final List<JsonNode> draftInputs = new ArrayList<>();
    private final List<JsonNode> reviewInputs = new ArrayList<>();
    private final Set<String> navigationUnitKeys = new LinkedHashSet<>();
    private boolean requestedM2;
    private boolean requestedM3;
    private boolean proposedSlices;

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      requests.add(request);
      return switch (request.taskKind()) {
        case "ACTIVITY_READING_PLAN" -> readingPlanResponse(request);
        case "ACTIVITY_DRAFT" -> {
          JsonNode input = canonicalJson.parseCanonical(request.untrustedInputJson());
          draftInputs.add(input);
          yield activityResponse(input, false);
        }
        case "ACTIVITY_REVIEW" -> {
          JsonNode input = canonicalJson.parseCanonical(request.untrustedInputJson());
          reviewInputs.add(input);
          yield activityResponse(input, true);
        }
        default -> throw new AssertionError("unexpected task kind " + request.taskKind());
      };
    }

    private StructuredModelResponse readingPlanResponse(StructuredModelRequest request) {
      JsonNode input = canonicalJson.parseCanonical(request.untrustedInputJson());
      JsonNode navigation = input.path("navigation");
      scalarText(navigation.path("items")).stream()
          .filter(value -> value.matches("M[0-9]+"))
          .forEach(navigationUnitKeys::add);
      int currentPage = navigation.path("currentPage").asInt();
      int totalPages = navigation.path("totalPages").asInt();
      String response;
      if (!navigationUnitKeys.contains("M2") || !navigationUnitKeys.contains("M3")) {
        if (currentPage < totalPages) {
          response = planResponse("[\"page-" + (currentPage + 1) + "\"]", "[]", "[]");
        } else {
          throw new AssertionError(
              "M2/M3 were absent from the shown Step05 navigation denominator");
        }
      } else if (!requestedM2) {
        requestedM2 = true;
        response = planResponse("[]", "[\"M2\"]", "[]");
      } else if (!requestedM3) {
        requestedM3 = true;
        response = planResponse("[]", "[\"M3\"]", "[]");
      } else if (!proposedSlices) {
        proposedSlices = true;
        response =
            planResponse(
                "[]",
                "[]",
                "[{\"sliceKey\":\"slice-m2\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M1\",\"M2\"],\"sharedContextUnitKeys\":[],\"scope\":\"validate scope\"},{\"sliceKey\":\"slice-m3\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M1\",\"M3\"],\"sharedContextUnitKeys\":[],\"scope\":\"write scope\"}]");
      } else {
        throw new AssertionError("unbounded reading-plan loop");
      }
      return response(response);
    }

    private StructuredModelResponse activityResponse(JsonNode input, boolean review) {
      boolean m2Scope = scalarText(input).contains(ActivityReadingCoordinatorTest.UNIT_TWO_BODY);
      ObjectNode response = JsonNodeFactory.instance.objectNode();
      ObjectNode activity = response.putArray("activities").addObject();
      activity.put("activityLocalId", m2Scope ? "slice-m2" : "slice-m3");
      activity.putArray("entryKeys").add("E1");
      activity.put("name", m2Scope ? "validate neutral input" : "write neutral record");
      activity.put("businessPurpose", "exercise formal bounded reading");
      activity.putArray("participants");
      activity.putArray("businessObjects").add("neutral-record");
      activity.putArray("triggerOrInput").add("entry");
      activity.putArray("conditions");
      activity.putArray("activitySteps").add("perform the selected complete scope");
      activity.putArray("codeDefinedResults").add("selected scope returns");
      activity.putArray("businessRules");
      activity.putArray("formulasOrMetrics");
      activity.putArray("terms");
      activity.put("certainty", "DIRECT_CODE_BEHAVIOR");
      activity.putArray("sourceRefs").add("S1");
      activity.putArray("questions");
      activity.putArray("scopeLimitations").add("M4 remains unread");
      if (review) {
        response.putArray("unexplainedEntries");
      }
      return response(
          new String(
              canonicalJson.encodeCanonical(response).copyToByteArray(), StandardCharsets.UTF_8));
    }

    private StructuredModelResponse response(String json) {
      return new StructuredModelResponse(
          ImmutableBytes.copyOf(json.getBytes(StandardCharsets.UTF_8)), IDENTITY);
    }

    private List<String> taskKinds() {
      return requests.stream().map(StructuredModelRequest::taskKind).toList();
    }

    private List<JsonNode> draftInputs() {
      return List.copyOf(draftInputs);
    }

    private List<JsonNode> reviewInputs() {
      return List.copyOf(reviewInputs);
    }
  }

  private static String planResponse(String pages, String units, String slices) {
    return "{\"requestedNavigationPages\":"
        + pages
        + ",\"requestedUnitKeys\":"
        + units
        + ",\"slices\":"
        + slices
        + ",\"unknowns\":[]}";
  }
}
