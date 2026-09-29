package org.sourceanalysis.app.analysis.interpretation.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.interpretation.ModelRuntimeIdentityV1;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** RED contract for bounded, denominator-preserving navigation of an oversized Step05 packet. */
class ActivityReadingCoordinatorTest {

  static final ModelRuntimeIdentityV1 IDENTITY =
      new ModelRuntimeIdentityV1("scripted", "large-reading", "xhigh", "read-only");
  // Full packet is roughly 19 KB; an entry plus selected M3 is roughly 7.5 KB.
  // The 16 KB reading budget therefore requires navigation while leaving room for one complete
  // selected unit, its compact navigation page, and the request envelope.
  static final String ENTRY_BODY = "FULL_ENTRY_BODY" + " e".repeat(100);
  static final String UNIT_TWO_BODY = "FULL_M2_BODY" + " two".repeat(1_500);
  static final String UNIT_THREE_BODY = "FULL_M3_BODY" + " three".repeat(1_200);
  static final String UNIT_FOUR_BODY = "FULL_M4_BODY" + " four".repeat(1_500);

  @Test
  void exposesTheBoundedLargePacketReadingBoundary() throws Exception {
    Class<?> coordinator = requiredType("ActivityReadingCoordinator");
    Class<?> profile = requiredType("ActivityReadingProfile");
    Class<?> plan = requiredType("ActivityReadingPlan");

    assertThat(coordinator.getConstructor(StructuredModelProvider.class)).isNotNull();
    assertThat(profile.getConstructor(int.class, int.class, int.class, int.class, int.class))
        .isNotNull();
    assertThat(
            coordinator
                .getMethod("coordinate", ActivityMaterialView.class, profile)
                .getReturnType())
        .isEqualTo(plan);
    assertThat(plan.getMethod("navigationPages").getReturnType()).isEqualTo(List.class);
    assertThat(plan.getMethod("unreadUnitKeys").getReturnType()).isEqualTo(List.class);
  }

  @Test
  void pagesTheWholeNavigationDenominatorAndNeverTreatsNavigationOnlyUnitsAsRead()
      throws Exception {
    NavigationProvider provider = new NavigationProvider();
    Object plan = coordinate(provider, largeView());

    assertThat(provider.requests)
        .extracting(StructuredModelRequest::taskKind)
        .isNotEmpty()
        .containsOnly("ACTIVITY_READING_PLAN");
    List<JsonNode> requests = provider.requestJson();
    assertThat(requests)
        .as("a navigation choice must be followed by a request that carries the full selected unit")
        .hasSizeGreaterThanOrEqualTo(2);
    int totalPages = 0;
    Set<Integer> pagesShown = new HashSet<>();
    Set<String> navigationUnitKeys = new HashSet<>();
    for (JsonNode request : requests) {
      JsonNode navigation = request.path("navigation");
      JsonNode items = navigation.path("items");
      if (!items.isArray() || items.size() == 0) {
        continue;
      }
      assertThat(navigation.path("totalItems").asInt()).isEqualTo(3);
      if (totalPages == 0) {
        totalPages = navigation.path("totalPages").asInt();
      }
      assertThat(navigation.path("totalPages").asInt()).isEqualTo(totalPages);
      assertThat(totalPages).isPositive();
      int currentPage = navigation.path("currentPage").asInt();
      assertThat(currentPage).isBetween(1, totalPages);
      pagesShown.add(currentPage);
      navigationUnitKeys.addAll(scalarText(items));
    }
    assertThat(totalPages).isPositive();
    for (int page = 1; page <= totalPages; page++) {
      assertThat(pagesShown)
          .as("every dynamically sized navigation page remains part of the shown denominator")
          .contains(page);
    }
    assertThat(navigationUnitKeys)
        .as("the complete navigation denominator is visible even when compact items share a page")
        .contains("M2", "M3", "M4");
    assertThat(requests)
        .anySatisfy(
            request ->
                assertThat(scalarText(request))
                    .as("a model-selected supplemental unit is sent as its entire persisted body")
                    .contains(UNIT_THREE_BODY)
                    .doesNotContain(UNIT_TWO_BODY, UNIT_FOUR_BODY));

    assertThat(stringList(plan, "unreadUnitKeys"))
        .as("shown but unselected navigation remains an explicit unread denominator")
        .containsExactlyInAnyOrder("M2", "M4");
    assertThat(stringList(plan, "sliceKeys")).containsExactly("slice-validation");
  }

  @Test
  void acceptsARepeatedRequestForTheAlreadyIncludedEntryMethod() throws Exception {
    StructuredModelProvider provider =
        request ->
            new StructuredModelResponse(
                ImmutableBytes.copyOf(
                    readingPlanResponse(
                            "[]",
                            "[\"M1\",\"M3\"]",
                            "[{\"sliceKey\":\"entry-with-write\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M1\",\"M3\"],\"sharedContextUnitKeys\":[],\"scope\":\"entry and saved implementation\"}]",
                            "[\"entry-with-write\"]",
                            true)
                        .getBytes(StandardCharsets.UTF_8)),
                IDENTITY);

    Object plan = coordinate(provider, largeView());

    assertThat(stringList(plan, "sliceKeys")).containsExactly("entry-with-write");
  }

  @Test
  void keepsASelectionRequestBoundedAfterSeveralCompleteUnitsHaveBeenSelected() throws Exception {
    List<JsonNode> inputs = new ArrayList<>();
    CanonicalJsonCodec codec = new CanonicalJsonCodec();
    StructuredModelProvider provider =
        request -> {
          inputs.add(codec.parseCanonical(request.untrustedInputJson()));
          String response =
              inputs.size() == 1
                  ? readingPlanResponse("[]", "[\"M2\",\"M3\",\"M4\"]", "[]", "[]", false)
                  : readingPlanResponse(
                      "[]",
                      "[]",
                      "[{\"sliceKey\":\"one-unit\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M3\"],\"sharedContextUnitKeys\":[],\"scope\":\"one independent action\"}]",
                      "[\"one-unit\"]",
                      true);
          return new StructuredModelResponse(
              ImmutableBytes.copyOf(response.getBytes(StandardCharsets.UTF_8)), IDENTITY);
        };

    Object plan = coordinate(provider, largeView());

    assertThat(inputs).hasSizeGreaterThanOrEqualTo(2);
    assertThat(stringList(plan, "sliceKeys")).containsExactly("one-unit");
    assertThat(scalarText(inputs.get(1).path("selectedUnitKeys"))).contains("M1", "M2", "M3", "M4");
    assertThat(scalarText(inputs.get(1).path("selectedUnitDirectory"))).contains("M2", "M3", "M4");
    assertThat(inputs.get(1).path("completeUnitsStatus").asText())
        .isEqualTo("PARTIAL_FOR_CAPACITY");
    ActivityReadingPlan actual = (ActivityReadingPlan) plan;
    assertThat(
            new String(
                actual.readingPackets().get(0).modelInputJson().copyToByteArray(),
                StandardCharsets.UTF_8))
        .contains(UNIT_THREE_BODY)
        .doesNotContain(UNIT_TWO_BODY, UNIT_FOUR_BODY);
  }

  static ActivityMaterialView largeView() {
    EntrySeed entry =
        new EntrySeed(
            "entry:large",
            "method:entry",
            new SourceRange(0, ENTRY_BODY.length(), 1, 1),
            "/large/submit");
    List<EntryCodeContext.MethodCode> methods =
        List.of(
            method("method:entry", "submit", ENTRY_BODY),
            method("method:two", "validate", UNIT_TWO_BODY),
            method("method:three", "write", UNIT_THREE_BODY),
            method("method:four", "audit", UNIT_FOUR_BODY));
    CodeReadingMaterialSet.Packet packet =
        new CodeReadingMaterialSet.Packet(
            "packet:large-reading",
            List.of(entry),
            methods,
            List.of(),
            new CodeReadingMaterialSet.PersistenceSelection(
                List.of(), List.of(), List.of(), List.of(), List.of()),
            List.of(
                source("S1", "method:entry"),
                source("S2", "method:two"),
                source("S3", "method:three"),
                source("S4", "method:four")),
            List.of(),
            List.of("LARGE_PACKET_FIXTURE"),
            ENTRY_BODY.length()
                + UNIT_TWO_BODY.length()
                + UNIT_THREE_BODY.length()
                + UNIT_FOUR_BODY.length());
    return new ActivityMaterialView(
        packet,
        new ActivityExplanationProfile(16_000, 8_000, 4, 32, 2_000),
        Map.of(entry.entryId(), "E1"),
        Map.of(
            "method:entry", "M1",
            "method:two", "M2",
            "method:three", "M3",
            "method:four", "M4"),
        Map.of(),
        Map.of(),
        Map.of("S1", "S1", "S2", "S2", "S3", "S3", "S4", "S4"),
        Map.of());
  }

  static Object coordinate(StructuredModelProvider provider, ActivityMaterialView view)
      throws Exception {
    Class<?> coordinatorType = requiredType("ActivityReadingCoordinator");
    Class<?> profileType = requiredType("ActivityReadingProfile");
    Constructor<?> profileConstructor =
        profileType.getConstructor(int.class, int.class, int.class, int.class, int.class);
    Object profile = profileConstructor.newInstance(20_000, 8_000, 3, 3, 4);
    Object coordinator =
        coordinatorType.getConstructor(StructuredModelProvider.class).newInstance(provider);
    try {
      return coordinatorType
          .getMethod("coordinate", ActivityMaterialView.class, profileType)
          .invoke(coordinator, view, profile);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause();
      if (cause instanceof Exception exception) {
        throw exception;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw failure;
    }
  }

  static List<String> stringList(Object value, String accessor) {
    try {
      Object result = value.getClass().getMethod(accessor).invoke(value);
      assertThat(result).isInstanceOf(List.class);
      @SuppressWarnings("unchecked")
      List<Object> items = (List<Object>) result;
      return items.stream().map(Object::toString).toList();
    } catch (ReflectiveOperationException missing) {
      throw new AssertionError("missing Activity reading plan accessor: " + accessor, missing);
    }
  }

  private static EntryCodeContext.MethodCode method(String key, String name, String body) {
    return new EntryCodeContext.MethodCode(
        key,
        "METHOD",
        "example.large.LargeService",
        name,
        "void " + name + "()",
        null,
        List.of(),
        "void",
        List.of(),
        List.of(),
        new EntryCodeContext.SourceSource(
            "src/main/java/example/large/LargeService.java", 1, 1, 0, body.length(), body),
        true,
        List.of(),
        List.of());
  }

  private static CodeReadingMaterialSet.SourceReference source(String ref, String methodKey) {
    return new CodeReadingMaterialSet.SourceReference(
        ref,
        new CodeReadingMaterialSet.UnitLocation(
            "JAVA_METHOD", methodKey, "src/main/java/example/large/LargeService.java", 1, 1));
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

  private static Class<?> requiredType(String simpleName) {
    try {
      return Class.forName(
          ActivityReadingCoordinatorTest.class.getPackageName() + "." + simpleName);
    } catch (ClassNotFoundException absent) {
      fail("missing direct large-material Activity type: " + simpleName, absent);
      throw new AssertionError("unreachable", absent);
    }
  }

  private static final class NavigationProvider implements StructuredModelProvider {
    private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    private final List<StructuredModelRequest> requests = new ArrayList<>();
    private boolean requestedM3;
    private boolean proposedSlice;

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      requests.add(request);
      if (!"ACTIVITY_READING_PLAN".equals(request.taskKind())) {
        throw new AssertionError("unexpected task kind " + request.taskKind());
      }
      JsonNode input = canonicalJson.parseCanonical(request.untrustedInputJson());
      JsonNode navigation = input.path("navigation");
      int currentPage = navigation.path("currentPage").asInt();
      int totalPages = navigation.path("totalPages").asInt();
      List<String> visibleNavigation = scalarText(navigation.path("items"));
      String response;
      if (requestedM3 && !proposedSlice) {
        proposedSlice = true;
        response =
            readingPlanResponse(
                "[]",
                "[]",
                "[{\"sliceKey\":\"slice-validation\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M1\",\"M3\"],\"sharedContextUnitKeys\":[],\"scope\":\"validate and write\"}]",
                "[\"slice-validation\"]",
                true);
      } else if (!visibleNavigation.contains("M3")) {
        if (currentPage >= totalPages) {
          throw new AssertionError("M3 was absent from the completed navigation denominator");
        }
        response =
            readingPlanResponse("[\"page-" + (currentPage + 1) + "\"]", "[]", "[]", "[]", false);
      } else if (!requestedM3) {
        requestedM3 = true;
        response = readingPlanResponse("[]", "[\"M3\"]", "[]", "[]", false);
      } else {
        throw new AssertionError("unbounded reading-plan loop");
      }
      return new StructuredModelResponse(
          ImmutableBytes.copyOf(response.getBytes(StandardCharsets.UTF_8)), IDENTITY);
    }

    private List<JsonNode> requestJson() {
      return requests.stream()
          .map(request -> canonicalJson.parseCanonical(request.untrustedInputJson()))
          .toList();
    }
  }

  private static String readingPlanResponse(
      String pages, String units, String slices, String finalSliceKeys, boolean finishReading) {
    return "{\"requestedNavigationPages\":"
        + pages
        + ",\"requestedUnitKeys\":"
        + units
        + ",\"slices\":"
        + slices
        + ",\"unknowns\":[],\"finalSliceKeys\":"
        + finalSliceKeys
        + ",\"supersededSlices\":[],\"finishReading\":"
        + finishReading
        + "}";
  }
}
