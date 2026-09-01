package com.linguan.codemd.target.artifacts;

/** Deep persistence seam for a module's immutable canonical payload set and final receipt. */
public interface CanonicalModuleArtifactStore {
    InstalledModulePublication install(ModuleInstallRequest request);

    ReopenedModulePublication reopen(ModulePublicationReference reference);
}
