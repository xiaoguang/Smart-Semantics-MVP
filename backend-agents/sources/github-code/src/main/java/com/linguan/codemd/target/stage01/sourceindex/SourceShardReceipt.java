package com.linguan.codemd.target.stage01.sourceindex;

import java.util.List;

/** Exact accounting receipt for one deterministic M2 verification shard. */
public record SourceShardReceipt(
        String shardId, List<String> denominatorFileIds, List<String> verifiedFileIds, String status, List<String> gapIds) {
    public SourceShardReceipt {
        if (shardId == null || !shardId.matches("source-shard:[0-9a-f]{64}")
                || denominatorFileIds == null || verifiedFileIds == null || gapIds == null
                || !"SUCCEEDED".equals(status)) {
            throw new SourceIndexException("SOURCE_HANDLE_INVALID", "source shard receipt is invalid");
        }
        denominatorFileIds = orderedFileIds(denominatorFileIds);
        verifiedFileIds = orderedFileIds(verifiedFileIds);
        gapIds = orderedGapIds(gapIds);
        if (!denominatorFileIds.equals(verifiedFileIds) || !gapIds.isEmpty()) {
            throw new SourceIndexException("SOURCE_HANDLE_INVALID", "a successful shard must close exactly");
        }
    }

    private static List<String> orderedFileIds(List<String> values) {
        String prior = null;
        for (String value : values) {
            if (value == null || !value.matches("file:[0-9a-f]{64}")
                    || (prior != null && prior.compareTo(value) >= 0)) {
                throw new SourceIndexException("SOURCE_HANDLE_INVALID", "shard file IDs must be sorted and unique");
            }
            prior = value;
        }
        return List.copyOf(values);
    }

    private static List<String> orderedGapIds(List<String> values) {
        String prior = null;
        for (String value : values) {
            if (value == null || value.isBlank() || (prior != null && prior.compareTo(value) >= 0)) {
                throw new SourceIndexException("SOURCE_HANDLE_INVALID", "shard gaps must be sorted and unique");
            }
            prior = value;
        }
        return List.copyOf(values);
    }
}
