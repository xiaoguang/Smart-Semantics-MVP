package org.sourceanalysis.app.analysis.interpretation.process;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Exact M6 Capsule Gap view projected into one M7-owned no-model process Gap. */
public record UpstreamFlowGapProjectionV1(List<String> sourceFlowSliceIds, JsonNode gapView) {

  private static final Set<String> GAP_VIEW_FIELDS =
      Set.of(
          "gapId",
          "scope",
          "reasonCode",
          "affectedSemanticIds",
          "evidenceRefs",
          "originKind",
          "originGapLedgerRef");

  /** Defensively copies the original canonical M6 Gap value without translating its meaning. */
  public UpstreamFlowGapProjectionV1 {
    sourceFlowSliceIds = orderedFlowIds(sourceFlowSliceIds);
    gapView = Objects.requireNonNull(gapView, "Gap view").deepCopy();
    requireExactGapView(gapView);
  }

  @Override
  public JsonNode gapView() {
    return gapView.deepCopy();
  }

  private static List<String> orderedFlowIds(List<String> values) {
    values = List.copyOf(Objects.requireNonNull(values, "source Flow slice IDs"));
    if (values.isEmpty()
        || values.stream().anyMatch(value -> value == null || value.isBlank())
        || values.size() != values.stream().distinct().count()
        || !values.equals(
            values.stream().sorted(BusinessProcessTaskCompiler::compareUtf8).toList())) {
      throw new IllegalArgumentException("source Flow slice IDs are invalid");
    }
    return values;
  }

  private static void requireExactGapView(JsonNode value) {
    if (!value.isObject() || !fieldNames(value).equals(GAP_VIEW_FIELDS)) {
      throw new IllegalArgumentException("upstream Flow Gap view is invalid");
    }
    requiredText(value, "gapId");
    requiredText(value, "scope");
    requiredText(value, "reasonCode");
    requiredText(value, "originKind");
    requireTextArray(value, "affectedSemanticIds", true);
    if (!value.path("evidenceRefs").isArray()) {
      throw new IllegalArgumentException("upstream Flow Gap evidence references are invalid");
    }
    JsonNode originGapLedgerRef = value.get("originGapLedgerRef");
    if (originGapLedgerRef == null
        || (!originGapLedgerRef.isNull() && !originGapLedgerRef.isObject())) {
      throw new IllegalArgumentException("upstream Flow Gap origin is invalid");
    }
  }

  private static Set<String> fieldNames(JsonNode value) {
    Set<String> fields = new HashSet<>();
    Iterator<String> names = value.fieldNames();
    names.forEachRemaining(fields::add);
    return fields;
  }

  private static void requiredText(JsonNode value, String field) {
    JsonNode candidate = value.get(field);
    if (candidate == null || !candidate.isTextual() || candidate.textValue().isBlank()) {
      throw new IllegalArgumentException("upstream Flow Gap " + field + " is invalid");
    }
  }

  private static void requireTextArray(JsonNode value, String field, boolean nonempty) {
    JsonNode candidate = value.get(field);
    if (!candidate.isArray()
        || (nonempty && candidate.isEmpty())
        || candidate.size() != textValues(candidate).size()) {
      throw new IllegalArgumentException("upstream Flow Gap " + field + " is invalid");
    }
  }

  private static Set<String> textValues(JsonNode values) {
    Set<String> result = new HashSet<>();
    for (JsonNode value : values) {
      if (!value.isTextual() || value.textValue().isBlank()) {
        return Set.of();
      }
      result.add(value.textValue());
    }
    return result;
  }
}
