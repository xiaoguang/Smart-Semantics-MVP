package org.sourceanalysis.app.analysis.discovery.frontend;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sourceanalysis.app.analysis.code.SourceRange;

/**
 * Finite observations for one literal page/component instance.
 *
 * <p>The context records source co-location only. Request IDs must not be read as a runtime
 * selection-to-save causality claim.
 */
public record FrontendPageContext(
    String contextId,
    String pagePath,
    String sourceSha256,
    String instanceKey,
    List<String> requestIds,
    List<FrontendPageSourceUnit> sourceUnits,
    List<FrontendPageObservation> observations,
    List<FrontendPageRequestCondition> requestConditions,
    List<FrontendPageLimitation> limitations) {

  public FrontendPageContext {
    if (contextId == null
        || contextId.isBlank()
        || pagePath == null
        || pagePath.isBlank()
        || sourceSha256 == null
        || !sourceSha256.matches("[0-9a-f]{64}")
        || instanceKey == null
        || instanceKey.isBlank()) {
      throw new IllegalArgumentException("frontend page context identity is invalid");
    }
    requestIds = immutableDistinct(requestIds, "frontend page context request IDs");
    sourceUnits = List.copyOf(sourceUnits);
    observations = List.copyOf(observations);
    requestConditions = List.copyOf(requestConditions);
    limitations = List.copyOf(limitations);

    Map<String, FrontendPageSourceUnit> units = new HashMap<>();
    for (FrontendPageSourceUnit sourceUnit : sourceUnits) {
      if (units.put(sourceUnit.unitRef(), sourceUnit) != null) {
        throw new IllegalArgumentException("frontend page context source-unit IDs are not unique");
      }
    }
    Set<String> observationIds = new HashSet<>();
    for (FrontendPageObservation observation : observations) {
      if (!observationIds.add(observation.observationId())
          || !units.containsKey(observation.fromUnitRef())
          || (observation.toUnitRef() != null && !units.containsKey(observation.toUnitRef()))
          || !contains(
              units.get(observation.fromUnitRef()).sourceUnitRange(), observation.callRange())) {
        throw new IllegalArgumentException("frontend page context observation is invalid");
      }
    }
    Set<String> conditionIds = new HashSet<>();
    for (FrontendPageRequestCondition condition : requestConditions) {
      FrontendPageSourceUnit unit = units.get(condition.unitRef());
      if (!conditionIds.add(condition.requestId() + "\u0000" + condition.branch())
          || !requestIds.contains(condition.requestId())
          || unit == null
          || !contains(unit.sourceUnitRange(), condition.range())) {
        throw new IllegalArgumentException("frontend page context request condition is invalid");
      }
    }
    for (FrontendPageLimitation limitation : limitations) {
      if (limitation.unitRef() != null && !units.containsKey(limitation.unitRef())) {
        throw new IllegalArgumentException("frontend page context limitation is invalid");
      }
    }
  }

  private static List<String> immutableDistinct(List<String> values, String label) {
    values = List.copyOf(values);
    if (values.stream().anyMatch(value -> value == null || value.isBlank())
        || new HashSet<>(values).size() != values.size()) {
      throw new IllegalArgumentException(label + " are invalid");
    }
    return values;
  }

  private static boolean contains(SourceRange outer, SourceRange inner) {
    long outerEnd = (long) outer.startOffsetUtf16() + outer.lengthUtf16();
    long innerEnd = (long) inner.startOffsetUtf16() + inner.lengthUtf16();
    return outer.startOffsetUtf16() <= inner.startOffsetUtf16() && outerEnd >= innerEnd;
  }
}
