package com.linguan.codemd.stage03;

import java.util.List;

/** Deterministic repository-level knowledge assembled from flows, meanings, and gaps. */
public record RepositoryBusinessModel(
        String schemaVersion, String repositoryBusinessModelId, String stage02ResultId,
        List<BusinessObject> objects, List<BusinessActivity> activities, List<BusinessFlow> flows,
        List<BusinessOutcome> outcomes, List<FieldDimension> fieldsAndDimensions,
        List<ObjectRelation> relations, List<MetricDefinition> metrics,
        List<AnswerableQuestion> exampleQuestions, List<PendingQuestion> pendingQuestions,
        List<KnowledgeConflict> conflicts, KnowledgeAccounting accounting) {
    public RepositoryBusinessModel {
        objects = List.copyOf(objects);
        activities = List.copyOf(activities);
        flows = List.copyOf(flows);
        outcomes = List.copyOf(outcomes);
        fieldsAndDimensions = List.copyOf(fieldsAndDimensions);
        relations = List.copyOf(relations);
        metrics = List.copyOf(metrics);
        exampleQuestions = List.copyOf(exampleQuestions);
        pendingQuestions = List.copyOf(pendingQuestions);
        conflicts = List.copyOf(conflicts);
    }
}
