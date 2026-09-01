package com.linguan.codemd.target.stage01.publish;

import com.linguan.codemd.target.artifacts.ArtifactReference;
import com.linguan.codemd.target.artifacts.ImmutableBytes;

/** Path-free composition boundary that reopens an exact immutable run input by its full reference. */
@FunctionalInterface
public interface Stage01InputArtifactRegistry {
    ImmutableBytes reopen(ArtifactReference reference);
}
