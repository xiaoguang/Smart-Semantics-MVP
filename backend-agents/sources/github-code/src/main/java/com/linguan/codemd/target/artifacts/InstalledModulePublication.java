package com.linguan.codemd.target.artifacts;

import java.util.List;

/** Result of a write-once module installation. */
public record InstalledModulePublication(
        ModulePublicationReference reference, String disposition, List<ArtifactDescriptor> artifactDescriptors) {}
