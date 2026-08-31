package com.linguan.codemd.stage01;

import java.nio.file.Path;
import java.util.List;

/** Complete M1 input; the local root is transport-only and never enters identity. */
public record FrozenRepositoryRequest(Origin origin, CaptureProof captureProof, Path snapshotRoot,
                                      InventoryScope inventoryScope, List<DeclaredFile> files,
                                      String verificationPolicyId, ResourceBudget resourceBudget,
                                      CapabilityProfileRef capabilityProfileRef) {
    public FrozenRepositoryRequest {
        files = files == null ? null : List.copyOf(files);
    }
}
