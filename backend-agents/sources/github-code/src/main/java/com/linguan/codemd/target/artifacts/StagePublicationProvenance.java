package com.linguan.codemd.target.artifacts;

/** Sealed provenance by which a stage proves its public semantic payload set. */
public sealed interface StagePublicationProvenance
        permits StagePublisherModuleProvenance, Stage08CoordinatorPreparationProvenance {}
