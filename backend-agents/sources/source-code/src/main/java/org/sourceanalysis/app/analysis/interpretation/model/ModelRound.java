package org.sourceanalysis.app.analysis.interpretation.model;

import org.sourceanalysis.app.artifact.Sha256Digest;

/** Receipt-bound canonical response for one actually started R1 or R2 call. */
public record ModelRound(String modelRoundId, String taskSpecId, String round, Sha256Digest canonicalResponseSha256) {}
