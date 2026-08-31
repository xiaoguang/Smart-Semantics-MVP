package com.linguan.codemd.stage01;

/** Public Stage 01 M1 seam. Later modules deliberately remain outside this slice. */
public final class Stage01Analyzer {
    private final SnapshotVerifier snapshotVerifier;

    public Stage01Analyzer() {
        this(new SnapshotVerifier());
    }

    Stage01Analyzer(SnapshotVerifier snapshotVerifier) {
        this.snapshotVerifier = snapshotVerifier;
    }

    /** Verifies one declared frozen inventory before any semantic parser can run. */
    public VerifiedSnapshot verify(FrozenRepositoryRequest request) {
        return snapshotVerifier.verify(request);
    }

    /** Verifies M1 first, then compiles only the re-opened declared verified bytes into M2. */
    public RepositoryUnderstanding understand(FrozenRepositoryRequest request) {
        VerifiedSnapshot snapshot = verify(request);
        return understand(snapshot, request, snapshotVerifier.reopenVerifiedBytes(request, snapshot));
    }

    /** Executes the M1 → M2 → M3 stage atomically; no partial Stage01Result is exposed. */
    public Stage01Result analyze(FrozenRepositoryRequest request) {
        VerifiedSnapshot snapshot = verify(request);
        java.util.Map<String, byte[]> verifiedBytes = snapshotVerifier.reopenVerifiedBytes(request, snapshot);
        RepositoryUnderstanding understanding = understand(snapshot, request, verifiedBytes);
        ProvenSourceFacts facts = new ProvenFactCompiler().compile(snapshot, understanding, verifiedBytes);
        String id = "stage01-result:" + sha256("stage01-result-v1\n" + snapshot.snapshotId() + "\n"
                + understanding.repositoryModel().repositoryModelId() + "\n"
                + understanding.capabilityReport().capabilityReportId() + "\n"
                + facts.provenFactSet().provenFactSetId() + "\n" + facts.proofPack().proofPackId()
                + "\n" + facts.gapLedger().gapLedgerId());
        return new Stage01Result("stage01-result-v1", id, snapshot, understanding, facts);
    }

    /** Typed-request overload retaining the frozen-request seam for existing callers. */
    public Stage01Result analyze(Stage01Request request) {
        if (request == null || !"gap-expectation-profile-v1".equals(request.gapExpectationProfileRef().profileId())
                || !"e63f976bba3fcc0acbc62c3b72f0a924d539d240d69ca86b7a598905fafc0f8a".equals(
                request.gapExpectationProfileRef().profileSha256())) {
            throw new Stage01Exception(Stage01FailureCode.PROFILE_REFERENCE_INVALID);
        }
        return analyze(request.frozenRepositoryRequest());
    }

    /** Returns the stable, flow-ready read projection without exposing M2 implementation records. */
    public Stage01FlowView flowView(Stage01Result result) {
        return new Stage01FlowViewCompiler().compile(result);
    }

    private RepositoryUnderstanding understand(VerifiedSnapshot snapshot, FrozenRepositoryRequest request,
                                               java.util.Map<String, byte[]> verifiedBytes) {
        return new RepositoryCompiler().understand(snapshot, request, verifiedBytes);
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }
}
