package com.linguan.codemd.target.stage02.applicationprofile;

import com.linguan.codemd.target.contracts.SourceExcerptV1;

/** One closed-union capability evidence reference, retaining exact source bytes when applicable. */
public record CapabilityEvidenceRefV2(
        Kind kind, SourceExcerptV1 sourceExcerpt, VersionedArtifactEvidenceV2 artifactEvidence) {
    public enum Kind {
        SOURCE_EXCERPT,
        ARTIFACT_REFERENCE
    }

    public CapabilityEvidenceRefV2 {
        if (kind == null
                || (kind == Kind.SOURCE_EXCERPT && (sourceExcerpt == null || artifactEvidence != null))
                || (kind == Kind.ARTIFACT_REFERENCE && (sourceExcerpt != null || artifactEvidence == null))) {
            throw new ApplicationProfileException(
                    "ENTRY_DISCOVERY_INVARIANT_BROKEN", "capability evidence union is invalid");
        }
    }

    public static CapabilityEvidenceRefV2 source(SourceExcerptV1 sourceExcerpt) {
        return new CapabilityEvidenceRefV2(Kind.SOURCE_EXCERPT, sourceExcerpt, null);
    }

    public static CapabilityEvidenceRefV2 artifact(VersionedArtifactEvidenceV2 artifactEvidence) {
        return new CapabilityEvidenceRefV2(Kind.ARTIFACT_REFERENCE, null, artifactEvidence);
    }
}
