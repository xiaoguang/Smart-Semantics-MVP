package com.linguan.codemd.target.artifacts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ModulePublicationAddressTest {
    @Test
    void permitsOnlyStageAndExternalValidationPublicationAddresses() {
        assertEquals(
                Set.of(StageModuleAddress.class, ValidationModuleAddress.class),
                Set.of(ModulePublicationAddress.class.getPermittedSubclasses()).stream()
                        .collect(Collectors.toUnmodifiableSet()));
    }

    @Test
    void acceptsOnlyContentAddressedRunAndValidationIdentitiesAtTheAddressBoundary() {
        String runId = "analysis-run:" + "a".repeat(64);

        assertEquals(
                runId,
                new StageModuleAddress(runId, 1, "freeze-source", 1, "request-admission").runId());
        assertEquals(
                "validation:" + "b".repeat(64),
                new ValidationModuleAddress(
                                runId, "validation:" + "b".repeat(64), 1, "run-validator")
                        .validationId());
    }

    @Test
    void permitsOnlyTheRunValidatorAsTheExternalValidationModule() {
        String runId = "analysis-run:" + "a".repeat(64);
        String validationId = "validation:" + "b".repeat(64);

        assertThrows(
                ArtifactStoreException.class,
                () -> new ValidationModuleAddress(runId, validationId, 2, "run-validator"));
        assertThrows(
                ArtifactStoreException.class,
                () -> new ValidationModuleAddress(runId, validationId, 1, "other-validator"));
    }

    @Test
    void requiresTheStageSpecificCompiledModuleKeyForEveryModuleNumber() {
        String runId = "analysis-run:" + "a".repeat(64);

        assertThrows(
                ArtifactStoreException.class,
                () -> new StageModuleAddress(runId, 1, "freeze-source", 3, "source-index"));
        assertThrows(
                ArtifactStoreException.class,
                () -> new StageModuleAddress(runId, 6, "interpret-one-flow-at-a-time", 6, "archive"));
    }
}
