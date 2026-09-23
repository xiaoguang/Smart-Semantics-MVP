package org.sourceanalysis.app.analysis.interpretation.activity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.adapter.provider.StructuredModelProvider;
import org.sourceanalysis.app.adapter.provider.StructuredModelRequest;
import org.sourceanalysis.app.adapter.provider.StructuredModelResponse;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/**
 * RED contract for the persisted reading-plan record and selected full-unit packet.
 *
 * <p>Navigation metadata is not a substitute for source text: this test only accepts a slice packet
 * when every selected unit is present in its full frozen body and every unselected unit is retained
 * as an explicit unread disposition in the versioned private plan record.
 */
class ActivityReadingPlanPersistenceTest {

  @Test
  void retainsShownPagesSelectionsAndUnprocessedUnitsInTheVersionedPrivatePlan() throws Exception {
    Object plan =
        ActivityReadingCoordinatorTest.coordinate(
            new SelectionProvider(), ActivityReadingCoordinatorTest.largeView());

    JsonNode privateRecord = privateRecord(plan);
    assertThat(privateRecord.path("schemaVersion").asText()).isEqualTo("activity-reading-plan-v1");
    assertThat(privateRecord.path("packetId").asText()).isEqualTo("packet:large-reading");
    assertThat(privateRecord.path("navigationPages").isArray()).isTrue();
    assertThat(privateRecord.path("navigationPages")).isNotEmpty();
    assertThat(scalarText(privateRecord))
        .contains("M1", "M3", "M2", "M4")
        .doesNotContain(
            ActivityReadingCoordinatorTest.UNIT_TWO_BODY,
            ActivityReadingCoordinatorTest.UNIT_FOUR_BODY);

    List<Object> packets = objectList(plan, "readingPackets");
    assertThat(packets).singleElement().satisfies(this::assertFullSelectedPacket);
  }

  private void assertFullSelectedPacket(Object packet) {
    try {
      Object bytes = packet.getClass().getMethod("modelInputJson").invoke(packet);
      assertThat(bytes).isInstanceOf(ImmutableBytes.class);
      JsonNode modelInput = new CanonicalJsonCodec().parseCanonical((ImmutableBytes) bytes);
      assertThat(scalarText(modelInput))
          .contains(
              ActivityReadingCoordinatorTest.ENTRY_BODY,
              ActivityReadingCoordinatorTest.UNIT_THREE_BODY)
          .doesNotContain(
              ActivityReadingCoordinatorTest.UNIT_TWO_BODY,
              ActivityReadingCoordinatorTest.UNIT_FOUR_BODY);
    } catch (ReflectiveOperationException missing) {
      throw new AssertionError(
          "reading packet must expose its immutable actual model input", missing);
    }
  }

  private static JsonNode privateRecord(Object plan) {
    try {
      Object record = plan.getClass().getMethod("toPrivateRecord").invoke(plan);
      assertThat(record).isInstanceOf(JsonNode.class);
      return (JsonNode) record;
    } catch (ReflectiveOperationException missing) {
      throw new AssertionError("missing persisted activity-reading-plan-v1 record", missing);
    }
  }

  private static List<Object> objectList(Object value, String accessor) {
    try {
      Object result = value.getClass().getMethod(accessor).invoke(value);
      assertThat(result).isInstanceOf(List.class);
      @SuppressWarnings("unchecked")
      List<Object> items = (List<Object>) result;
      return items;
    } catch (ReflectiveOperationException missing) {
      throw new AssertionError("missing Activity reading plan accessor: " + accessor, missing);
    }
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

  private static final class SelectionProvider implements StructuredModelProvider {
    private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    private boolean requestedM3;
    private boolean proposedSlice;

    @Override
    public StructuredModelResponse generate(StructuredModelRequest request) {
      if (!"ACTIVITY_READING_PLAN".equals(request.taskKind())) {
        throw new AssertionError("unexpected task kind " + request.taskKind());
      }
      JsonNode input = canonicalJson.parseCanonical(request.untrustedInputJson());
      JsonNode navigation = input.path("navigation");
      List<String> visible = scalarText(navigation.path("items"));
      int currentPage = navigation.path("currentPage").asInt();
      int totalPages = navigation.path("totalPages").asInt();
      String response;
      if (requestedM3 && !proposedSlice) {
        proposedSlice = true;
        response =
            "{\"requestedNavigationPages\":[],\"requestedUnitKeys\":[],\"slices\":[{\"sliceKey\":\"slice-validation\",\"entryKeys\":[\"E1\"],\"requiredUnitKeys\":[\"M1\",\"M3\"],\"sharedContextUnitKeys\":[],\"scope\":\"validate and write\"}],\"unknowns\":[]}";
      } else if (!visible.contains("M3")) {
        if (currentPage >= totalPages) {
          throw new AssertionError("M3 was absent from the completed navigation denominator");
        }
        response =
            "{\"requestedNavigationPages\":[\"page-"
                + (currentPage + 1)
                + "\"],\"requestedUnitKeys\":[],\"slices\":[],\"unknowns\":[]}";
      } else if (!requestedM3) {
        requestedM3 = true;
        response =
            "{\"requestedNavigationPages\":[],\"requestedUnitKeys\":[\"M3\"],\"slices\":[],\"unknowns\":[]}";
      } else {
        throw new AssertionError("unbounded reading-plan loop");
      }
      return new StructuredModelResponse(
          ImmutableBytes.copyOf(response.getBytes(StandardCharsets.UTF_8)),
          ActivityReadingCoordinatorTest.IDENTITY);
    }
  }
}
