package com.linguan.codemd.target.artifacts;

/** Typed publication address. File-system derivation remains inside the store. */
public sealed interface ModulePublicationAddress permits StageModuleAddress, ValidationModuleAddress {
    String runId();

    int moduleNumber();

    String moduleKey();
}
