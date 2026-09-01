package com.linguan.codemd.target.artifacts;

import java.util.Comparator;
import java.util.List;

/**
 * Compiled-in public semantic filename/type/version sets.
 *
 * <p>The Foundation vertical slice activates the Stage01 set. Later stages add their published
 * sets here before their publication specifiers are implemented; unregistered stages fail closed.
 */
final class StageSemanticSchemaRegistry {
    private static final List<Requirement> STAGE01 =
            List.of(
                    new Requirement(
                            "source-input.json", "STAGE01_SOURCE_INPUT", "stage01-source-input-v2"),
                    new Requirement(
                            "source-inventory.jsonl", "STAGE01_SOURCE_INVENTORY", "stage01-source-inventory-v2"),
                    new Requirement(
                            "verified-snapshot.json", "VERIFIED_SNAPSHOT", "verified-snapshot-v2"));

    private StageSemanticSchemaRegistry() {}

    static void requireExact(StagePublicationAddress address, List<ArtifactDescriptor> descriptors) {
        List<Requirement> requirements = requirements(address);
        if (descriptors.size() != requirements.size()) {
            throw new ArtifactStoreException(
                    "STAGE_PUBLICATION_INVALID", "semantic artifact count differs from registered stage set");
        }
        List<ArtifactDescriptor> sorted = descriptors.stream().sorted(byFileName()).toList();
        for (int index = 0; index < requirements.size(); index++) {
            Requirement requirement = requirements.get(index);
            ArtifactDescriptor descriptor = sorted.get(index);
            if (!requirement.fileName().equals(descriptor.fileName())
                    || !requirement.artifactType().equals(descriptor.artifactType())
                    || !requirement.schemaVersion().equals(descriptor.schemaVersion())) {
                throw new ArtifactStoreException(
                        "STAGE_PUBLICATION_INVALID", "semantic artifact does not match registered stage set");
            }
        }
    }

    static void requirePublisherPayloads(
            ModulePublicationAddress address, List<ArtifactDescriptor> descriptors) {
        if (!(address instanceof StageModuleAddress stage) || !"publish".equals(stage.moduleKey())) {
            return;
        }
        requireExact(new StagePublicationAddress(stage.runId(), stage.stageNumber(), stage.stageKey()), descriptors);
    }

    private static List<Requirement> requirements(StagePublicationAddress address) {
        if (address.stageNumber() == 1) {
            return STAGE01;
        }
        throw new ArtifactStoreException(
                "STAGE_PUBLICATION_SCHEMA_UNAVAILABLE",
                "semantic schema set is not implemented for stage " + address.stageNumber());
    }

    private static Comparator<ArtifactDescriptor> byFileName() {
        return Comparator.comparing(ArtifactDescriptor::fileName);
    }

    private record Requirement(String fileName, String artifactType, String schemaVersion) {}
}
