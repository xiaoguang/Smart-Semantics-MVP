package com.linguan.codemd.target.artifacts;

import java.util.List;
import java.util.Map;

/** Closed, versioned mapping between persisted stage/module numbers and their wire keys. */
final class StageDefinitions {
    private static final Map<Integer, Definition> DEFINITIONS =
            Map.of(
                    1, new Definition("freeze-source", List.of("request-admission", "source-index", "publish")),
                    2, new Definition("discover-application-and-entries", List.of("application-profile", "http-entry", "mapper-catalog", "publish")),
                    3, new Definition("build-five-program-graphs", List.of("code-structure", "call-graph", "control-flow", "data-flow", "evidence-graph", "publish")),
                    4, new Definition("prove-code-facts", List.of("candidates", "proofs", "publish")),
                    5, new Definition("compile-business-flows", List.of("flow-compiler", "capsule-projector", "publish")),
                    6, new Definition("interpret-one-flow-at-a-time", List.of("registry-task-compiler", "registry-proposal-runner", "registry-freezer", "flow-task-compiler", "interpretation-runner", "publish")),
                    7, new Definition("admit-and-merge-business-knowledge", List.of("admission", "knowledge-merge", "publish")),
                    8, new Definition("build-nine-section-document-and-archive", List.of("planner", "renderer", "trace", "archive")));

    private StageDefinitions() {}

    static Definition require(int stageNumber, String stageKey) {
        Definition definition = DEFINITIONS.get(stageNumber);
        if (definition == null || !definition.stageKey().equals(stageKey)) {
            throw new ArtifactStoreException(
                    "MODULE_PUBLICATION_REQUEST_INVALID", "stage number/key is not registered");
        }
        return definition;
    }

    static void requireModule(int stageNumber, String stageKey, int moduleNumber, String moduleKey) {
        Definition definition = require(stageNumber, stageKey);
        if (moduleNumber < 1
                || moduleNumber > definition.moduleKeys().size()
                || !definition.moduleKeys().get(moduleNumber - 1).equals(moduleKey)) {
            throw new ArtifactStoreException(
                    "MODULE_PUBLICATION_REQUEST_INVALID", "module number/key is not registered for stage");
        }
    }

    static int publisherModuleNumber(int stageNumber, String stageKey) {
        return require(stageNumber, stageKey).moduleKeys().size();
    }

    record Definition(String stageKey, List<String> moduleKeys) {}
}
