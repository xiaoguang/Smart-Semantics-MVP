package org.sourceanalysis.app.analysis.interpretation.activity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialProfile;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.runtime.modeljob.ModelJobExecutionConfiguration;
import org.sourceanalysis.app.runtime.modeljob.ModelJobProviderBinding;

/**
 * RED contracts for Step05 packet failure boundaries.
 *
 * <p>These use oversized persisted packets deliberately: the job boundary must include bounded
 * READING_PLAN and a complete selected slice, rather than exercising the legacy M10 job seam.
 */
class ActivityPacketFailureIsolationTest {

  static final ActivityExplanationProfile PROFILE =
      new ActivityExplanationProfile(20_000, 8_000, 4, 32, 2_000);
  static final ModelRuntimeIdentityV1 IDENTITY_A =
      new ModelRuntimeIdentityV1("scripted-a", "packet-isolation", "xhigh", "read-only");
  static final ModelRuntimeIdentityV1 IDENTITY_B =
      new ModelRuntimeIdentityV1("scripted-b", "packet-isolation", "xhigh", "read-only");

  @Test
  void keepsOtherPacketTerminalResultsWhenOnePacketDraftFails() {
    PacketScriptProvider provider = new PacketScriptProvider(IDENTITY_A, "packet:alpha");

    ActivityExplanationResult result =
        new ActivityExplainer(provider)
            .explain(
                new ExplainCodeReadingMaterialsRequest(
                    materialSet(
                        packet("packet:alpha", "entry:alpha", "ALPHA"),
                        packet("packet:bravo", "entry:bravo", "BRAVO")),
                    PROFILE,
                    2));

    assertThat(provider.completedPackets())
        .as("a terminal failure for alpha must not stop an independent persisted packet")
        .containsExactly("packet:bravo");
    assertThat(provider.draftPacketIds()).containsExactlyInAnyOrder("packet:alpha", "packet:bravo");
    assertThat(provider.reviewPacketIds()).containsExactly("packet:bravo");
    assertThat(result.coverage())
        .extracting(ActivityEntryCoverage::entryId)
        .containsExactlyInAnyOrder("entry:alpha", "entry:bravo");
    assertThat(result.coverage())
        .anySatisfy(
            coverage -> {
              assertThat(coverage.entryId()).isEqualTo("entry:alpha");
              assertThat(coverage.disposition()).isEqualTo("NOT_ANALYZED");
              assertThat(coverage.reasonCode()).isEqualTo("ACTIVITY_PROVIDER_FAILED_AFTER_START");
            });
  }

  @Test
  void stopsOnlyTheAuthenticationFailedBindingWhileOtherStableBindingContinues(
      @TempDir Path temporary) throws Exception {
    AuthenticationFailedProvider unavailable = new AuthenticationFailedProvider();
    PacketScriptProvider available = new PacketScriptProvider(IDENTITY_B, null);
    ActivityExplainer explainer =
        configuredExplainer(unavailable, available, temporary.resolve("journal"));

    ActivityExplanationResult result =
        explainer.explain(
            new ExplainCodeReadingMaterialsRequest(
                materialSet(
                    packet("packet:01-auth", "entry:01-auth", "AUTH"),
                    packet("packet:02-open", "entry:02-open", "OPEN"),
                    packet("packet:03-blocked", "entry:03-blocked", "BLOCKED"),
                    packet("packet:04-open", "entry:04-open", "OPEN_AGAIN")),
                PROFILE,
                4));

    assertThat(unavailable.readingPacketIds())
        .as("a confirmed credential failure closes only its fixed binding queue")
        .containsExactly("packet:01-auth");
    assertThat(available.completedPackets())
        .as("the other fixed binding remains eligible; no fallback changes the failed route")
        .containsExactlyInAnyOrder("packet:02-open", "packet:04-open");
    assertThat(result.coverage())
        .anySatisfy(
            coverage -> {
              assertThat(coverage.entryId()).isEqualTo("entry:03-blocked");
              assertThat(coverage.disposition()).isEqualTo("NOT_ANALYZED");
              assertThat(coverage.reasonCode()).isEqualTo("BLOCKED_BY_PROVIDER");
            });
    try (var paths = Files.walk(temporary.resolve("journal"))) {
      assertThat(paths.filter(path -> path.getFileName().toString().equals("failed-result.json")))
          .hasSize(2);
    }
  }

  static CodeReadingMaterialSet materialSet(CodeReadingMaterialSet.Packet... packets) {
    AnalysisRunId run = AnalysisRunId.parse("analysis-run:" + "5".repeat(64));
    List<CodeReadingMaterialSet.Packet> values = List.of(packets);
    return new CodeReadingMaterialSet(
        new CodeReadingMaterialSet.Header(
            new VerifiedSourceInventoryReference(
                publication(run, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, '1')),
            new ProgramGraphsReference(publication(run, AnalysisStepKey.PROGRAM_GRAPHS, '2')),
            publication(run, AnalysisStepKey.PROVEN_CODE_FACTS, '3'),
            "snapshot:" + "5".repeat(64),
            new CodeReadingMaterialProfile(256_000L, 16)),
        values,
        values.stream()
            .map(
                packet ->
                    new CodeReadingMaterialSet.EntryCoverage(
                        packet.entries().get(0).entryId(),
                        List.of(packet.packetId()),
                        CodeReadingMaterialSet.CoverageStatus.COLLECTED,
                        List.of()))
            .toList());
  }

  static CodeReadingMaterialSet.Packet packet(String packetId, String entryId, String marker) {
    String entryKey = "method:" + marker.toLowerCase() + ":entry";
    String selectedKey = "method:" + marker.toLowerCase() + ":selected";
    String unreadKey = "method:" + marker.toLowerCase() + ":unread";
    String entryBody = marker + "_ENTRY_BODY submit();";
    String selectedBody = marker + "_SELECTED_COMPLETE_BODY" + " work".repeat(1_650);
    String unreadBody = marker + "_UNREAD_COMPLETE_BODY" + " audit".repeat(1_650);
    EntrySeed entry =
        new EntrySeed(
            entryId,
            entryKey,
            new SourceRange(0, entryBody.length(), 1, 1),
            "/" + marker.toLowerCase() + "/submit");
    EntryCodeContext.CallSite call =
        new EntryCodeContext.CallSite(
            "call:" + marker.toLowerCase() + ":selected",
            entryKey,
            "METHOD",
            new SourceRange(0, entryBody.length(), 1, 1),
            new SourceRange(0, entryBody.length(), 1, 1),
            "selected()",
            List.of(
                new EntryCodeContext.CallTarget(
                    selectedKey,
                    List.of("DECLARATION"),
                    marker + ".selected",
                    List.of("CALL_HIERARCHY"),
                    "BODY_INCLUDED",
                    null,
                    List.of())));
    return new CodeReadingMaterialSet.Packet(
        packetId,
        List.of(entry),
        List.of(
            method(entryKey, "submit", entryBody, marker),
            method(selectedKey, "selected", selectedBody, marker),
            method(unreadKey, "audit", unreadBody, marker)),
        List.of(new CodeReadingMaterialSet.EntryCall(entryId, call)),
        new CodeReadingMaterialSet.PersistenceSelection(
            List.of(), List.of(), List.of(), List.of(), List.of()),
        List.of(
            source("S1", entryKey, marker),
            source("S2", selectedKey, marker),
            source("S3", unreadKey, marker)),
        List.of(),
        List.of("packet fixture " + marker),
        entryBody.length() + selectedBody.length() + unreadBody.length());
  }

  static StructuredModelResponse readingResponse(ModelRuntimeIdentityV1 identity, String json) {
    return new StructuredModelResponse(
        ImmutableBytes.copyOf(json.getBytes(StandardCharsets.UTF_8)), identity);
  }

  static String packetIdForActivityInput(JsonNode input) {
    List<String> values = scalarText(input.path("readingPacket"));
    if (values.stream().anyMatch(value -> value.contains("ALPHA_"))) {
      return "packet:alpha";
    }
    if (values.stream().anyMatch(value -> value.contains("BRAVO_"))) {
      return "packet:bravo";
    }
    for (String marker : List.of("AUTH", "OPEN_AGAIN", "OPEN", "BLOCKED")) {
      if (values.stream().anyMatch(value -> value.contains(marker + "_"))) {
        return switch (marker) {
          case "AUTH" -> "packet:01-auth";
          case "OPEN" -> "packet:02-open";
          case "BLOCKED" -> "packet:03-blocked";
          default -> "packet:04-open";
        };
      }
    }
    throw new AssertionError("activity input has no packet-local marker: " + input);
  }

  static List<String> scalarText(JsonNode value) {
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

  private static ActivityExplainer configuredExplainer(
      StructuredModelProvider unavailable, StructuredModelProvider available, Path journal)
      throws Exception {
    Files.createDirectory(journal);
    ModelJobProviderBinding first =
        new ModelJobProviderBinding("first", "first-quota", 1, unavailable, IDENTITY_A);
    ModelJobProviderBinding second =
        new ModelJobProviderBinding("second", "second-quota", 1, available, IDENTITY_B);
    ModelJobExecutionConfiguration configuration =
        new ModelJobExecutionConfiguration(
            2,
            Map.of("first", first, "second", second),
            Map.of(
                "activity", List.of("first", "second"),
                "processGroup", List.of("first"),
                "repositorySummary", List.of("first"),
                "report", List.of("first")),
            journal,
            AnalysisRunId.parse("analysis-run:" + "6".repeat(64)));
    return ActivityExplainer.forExecution(configuration);
  }

  private static EntryCodeContext.MethodCode method(
      String methodKey, String name, String body, String marker) {
    return new EntryCodeContext.MethodCode(
        methodKey,
        "METHOD",
        "example." + marker.toLowerCase(),
        name,
        "void " + name + "()",
        null,
        List.of(),
        "void",
        List.of(),
        List.of(),
        new EntryCodeContext.SourceSource(
            "src/main/java/example/" + marker.toLowerCase() + "/Service.java",
            1,
            1,
            0,
            body.length(),
            body),
        true,
        List.of(),
        List.of());
  }

  private static CodeReadingMaterialSet.SourceReference source(
      String reference, String methodKey, String marker) {
    return new CodeReadingMaterialSet.SourceReference(
        reference,
        new CodeReadingMaterialSet.UnitLocation(
            "JAVA_METHOD",
            methodKey,
            "src/main/java/example/" + marker.toLowerCase() + "/Service.java",
            1,
            1));
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

  static final class PacketScriptProvider implements StructuredModelProvider {
    private final ModelRuntimeIdentityV1 identity;
    private final String terminalDraftFailurePacket;
    private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    private final Map<String, Integer> readingRounds = new LinkedHashMap<>();
    private final List<String> readingPacketIds = new ArrayList<>();
    private final List<String> draftPacketIds = new ArrayList<>();
    private final List<String> reviewPacketIds = new ArrayList<>();

    PacketScriptProvider(ModelRuntimeIdentityV1 identity, String terminalDraftFailurePacket) {
      this.identity = identity;
      this.terminalDraftFailurePacket = terminalDraftFailurePacket;
    }

    @Override
    public synchronized StructuredModelResponse generate(StructuredModelRequest request) {
      JsonNode input = canonicalJson.parseCanonical(request.untrustedInputJson());
      return switch (request.taskKind()) {
        case "ACTIVITY_READING_PLAN" -> readingPlan(input);
        case "ACTIVITY_DRAFT" -> draft(input);
        case "ACTIVITY_REVIEW" -> review(input);
        default -> throw new AssertionError("unexpected Step05 task " + request.taskKind());
      };
    }

    private StructuredModelResponse readingPlan(JsonNode input) {
      String packetId = input.path("packetId").asText();
      if (packetId.isBlank()) {
        throw new AssertionError("READING_PLAN lost persisted packet identity");
      }
      readingPacketIds.add(packetId);
      int round = readingRounds.merge(packetId, 1, Integer::sum);
      if (round == 1) {
        return readingResponse(
            identity,
            "{\"requestedNavigationPages\":[],\"requestedUnitKeys\":[\"M2\"],\"slices\":[],\"unknowns\":[]}");
      }
      if (round == 2) {
        return readingResponse(
            identity,
            "{\"requestedNavigationPages\":[],\"requestedUnitKeys\":[],\"slices\":[{\"sliceKey\":\"selected-scope\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M1\",\"M2\"],\"sharedContextUnitKeys\":[],\"scope\":\"selected complete scope\"}],\"unknowns\":[]}");
      }
      throw new AssertionError("unexpected extra READING_PLAN round for " + packetId);
    }

    private StructuredModelResponse draft(JsonNode input) {
      String packetId = packetIdForActivityInput(input);
      draftPacketIds.add(packetId);
      if (packetId.equals(terminalDraftFailurePacket)) {
        throw new StructuredModelProviderFailure("INVALID_JSON", true, true);
      }
      return readingResponse(identity, "{\"activities\":[]}");
    }

    private StructuredModelResponse review(JsonNode input) {
      String packetId = packetIdForActivityInput(input);
      reviewPacketIds.add(packetId);
      return readingResponse(identity, "{\"activities\":[],\"unexplainedEntries\":[\"E1\"]}");
    }

    List<String> readingPacketIds() {
      return List.copyOf(readingPacketIds);
    }

    List<String> draftPacketIds() {
      return List.copyOf(draftPacketIds);
    }

    List<String> reviewPacketIds() {
      return List.copyOf(reviewPacketIds);
    }

    List<String> completedPackets() {
      return reviewPacketIds();
    }
  }

  private static final class AuthenticationFailedProvider implements StructuredModelProvider {
    private final List<String> readingPacketIds = new ArrayList<>();

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      JsonNode input = new CanonicalJsonCodec().parseCanonical(request.untrustedInputJson());
      if ("ACTIVITY_READING_PLAN".equals(request.taskKind())) {
        readingPacketIds.add(input.path("packetId").asText());
      }
      throw new StructuredModelProviderFailure("AUTHENTICATION_FAILED", false, false);
    }

    private List<String> readingPacketIds() {
      return List.copyOf(readingPacketIds);
    }
  }
}
