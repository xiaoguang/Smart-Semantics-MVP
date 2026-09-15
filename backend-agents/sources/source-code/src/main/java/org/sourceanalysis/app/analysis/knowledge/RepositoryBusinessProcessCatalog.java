package org.sourceanalysis.app.analysis.knowledge;

import java.util.List;

/** Consolidated repository-wide business process catalog. */
public record RepositoryBusinessProcessCatalog(
    List<BusinessArea> businessAreas,
    List<BusinessAlias> aliases,
    List<BusinessProcess> processes,
    List<ProcessRelation> processRelations,
    List<String> standaloneActivityIds,
    List<String> unclassifiedActivityIds,
    List<KnowledgeItem> directActivityKnowledgeItems,
    List<String> pendingConfirmations) {

  public RepositoryBusinessProcessCatalog {
    businessAreas = List.copyOf(businessAreas);
    aliases = List.copyOf(aliases);
    processes = List.copyOf(processes);
    processRelations = List.copyOf(processRelations);
    standaloneActivityIds = List.copyOf(standaloneActivityIds);
    unclassifiedActivityIds = List.copyOf(unclassifiedActivityIds);
    directActivityKnowledgeItems = List.copyOf(directActivityKnowledgeItems);
    pendingConfirmations = List.copyOf(pendingConfirmations);
  }

  /** One model-reviewed repository business term and its observed equivalent names. */
  public record BusinessAlias(String canonicalName, List<String> aliases) {
    public BusinessAlias {
      require(canonicalName, "canonical business name");
      aliases = List.copyOf(aliases);
    }
  }

  public record BusinessArea(String areaId, String name, String purpose, List<String> processIds) {
    public BusinessArea {
      require(areaId, "business area ID");
      require(name, "business area name");
      require(purpose, "business area purpose");
      processIds = List.copyOf(processIds);
    }
  }

  public record BusinessProcess(
      String processId,
      String name,
      String purpose,
      String scope,
      List<String> participants,
      List<String> businessObjects,
      List<ActivityUse> activityUses,
      List<ProcessStage> stages,
      List<String> branches,
      List<BusinessRule> businessRules,
      List<String> endResults,
      List<String> supportActivityUseIds,
      List<KnowledgeItem> knowledgeItems,
      List<String> pendingConnections,
      List<String> sourceRefs) {
    public BusinessProcess {
      require(processId, "business process ID");
      require(name, "business process name");
      require(purpose, "business process purpose");
      require(scope, "business process scope");
      participants = List.copyOf(participants);
      businessObjects = List.copyOf(businessObjects);
      activityUses = List.copyOf(activityUses);
      stages = List.copyOf(stages);
      branches = List.copyOf(branches);
      businessRules = List.copyOf(businessRules);
      endResults = List.copyOf(endResults);
      supportActivityUseIds = List.copyOf(supportActivityUseIds);
      knowledgeItems = List.copyOf(knowledgeItems);
      pendingConnections = List.copyOf(pendingConnections);
      sourceRefs = List.copyOf(sourceRefs);
      if (stages.isEmpty()) {
        throw new IllegalArgumentException("published business process requires a stage");
      }
    }
  }

  public record ActivityUse(
      String activityUseId,
      String activityId,
      String role,
      String variant,
      List<String> statementRefs,
      List<String> sourceRefs) {
    public ActivityUse {
      require(activityUseId, "activity use ID");
      require(activityId, "activity ID");
      require(role, "activity role");
      require(variant, "activity variant");
      statementRefs = List.copyOf(statementRefs);
      sourceRefs = List.copyOf(sourceRefs);
    }
  }

  public record ProcessStage(
      int order,
      String name,
      String narrative,
      List<String> activityUseIds,
      List<String> entryConditions,
      List<String> actions,
      List<String> stateChanges,
      List<String> rejectionConditions,
      List<String> outcomes,
      List<String> transitions,
      String certainty,
      List<String> statementRefs,
      List<String> sourceRefs) {
    public ProcessStage {
      if (order < 1) {
        throw new IllegalArgumentException("process stage order must be positive");
      }
      require(name, "process stage name");
      require(narrative, "process stage narrative");
      activityUseIds = List.copyOf(activityUseIds);
      entryConditions = List.copyOf(entryConditions);
      actions = List.copyOf(actions);
      stateChanges = List.copyOf(stateChanges);
      rejectionConditions = List.copyOf(rejectionConditions);
      outcomes = List.copyOf(outcomes);
      transitions = List.copyOf(transitions);
      certainty = validateCertainty(certainty);
      statementRefs = List.copyOf(statementRefs);
      sourceRefs = List.copyOf(sourceRefs);
    }
  }

  public record BusinessRule(
      String subject,
      String when,
      String actionOrDecision,
      String otherwise,
      String result,
      String certainty,
      List<String> activityUseIds,
      List<String> statementRefs,
      List<String> sourceRefs) {
    public BusinessRule {
      require(subject, "business rule subject");
      require(when, "business rule condition");
      require(actionOrDecision, "business rule action");
      require(result, "business rule result");
      certainty = validateCertainty(certainty);
      if (activityUseIds == null || activityUseIds.isEmpty()) {
        throw new IllegalArgumentException("business rule activity use IDs are required");
      }
      activityUseIds.forEach(
          activityUseId -> require(activityUseId, "business rule activity use IDs"));
      activityUseIds = List.copyOf(activityUseIds);
      statementRefs = List.copyOf(statementRefs);
      sourceRefs = List.copyOf(sourceRefs);
    }
  }

  public record KnowledgeItem(
      String kind,
      String text,
      String ownerId,
      String certainty,
      List<String> statementRefs,
      List<String> sourceRefs) {
    public KnowledgeItem {
      if (!List.of(
              "OBJECT", "FIELD_OR_DIMENSION", "OBJECT_RELATION", "FORMULA_OR_METRIC", "QUESTION")
          .contains(kind)) {
        throw new IllegalArgumentException("knowledge item kind is invalid");
      }
      require(text, "knowledge item text");
      require(ownerId, "knowledge item owner");
      certainty = validateCertainty(certainty);
      statementRefs = List.copyOf(statementRefs);
      sourceRefs = List.copyOf(sourceRefs);
    }
  }

  public record ProcessRelation(
      String fromProcessId,
      String toProcessId,
      String relationType,
      String description,
      String certainty,
      List<String> sourceRefs) {
    public ProcessRelation {
      require(fromProcessId, "process relation source");
      require(toProcessId, "process relation target");
      if (!List.of("PARENT_CHILD", "RELATED", "ALTERNATIVE").contains(relationType)) {
        throw new IllegalArgumentException("process relation type is invalid");
      }
      require(description, "process relation description");
      certainty = validateCertainty(certainty);
      sourceRefs = List.copyOf(sourceRefs);
    }
  }

  private static String validateCertainty(String value) {
    if (!List.of("CONFIRMED", "INFERRED", "UNRESOLVED").contains(value)) {
      throw new IllegalArgumentException("process certainty is invalid");
    }
    return value;
  }

  private static void require(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
  }
}
