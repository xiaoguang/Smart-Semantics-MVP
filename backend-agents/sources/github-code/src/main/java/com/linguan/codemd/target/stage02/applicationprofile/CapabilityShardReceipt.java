package com.linguan.codemd.target.stage02.applicationprofile;

import java.util.List;

/** Exact denominator and completion evidence for one deterministic Stage02 capability shard. */
public record CapabilityShardReceipt(
        String shardId,
        List<String> denominatorSiteIds,
        List<String> dispositionSiteIds,
        String status,
        List<String> gapIds) {
    public CapabilityShardReceipt {
        if (shardId == null
                || !shardId.matches("[a-z][a-z0-9-]*-shard:[0-9a-f]{64}")
                || denominatorSiteIds == null
                || dispositionSiteIds == null
                || gapIds == null
                || (!"SUCCEEDED".equals(status) && !"SUCCEEDED_WITH_GAPS".equals(status))) {
            throw new ApplicationProfileException("ENTRY_DISCOVERY_INVARIANT_BROKEN", "capability shard fields are invalid");
        }
        denominatorSiteIds = sortedUnique(denominatorSiteIds, "site");
        dispositionSiteIds = sortedUnique(dispositionSiteIds, "site");
        gapIds = sortedUnique(gapIds, "gap");
        if (!denominatorSiteIds.equals(dispositionSiteIds)
                || ("SUCCEEDED".equals(status) && !gapIds.isEmpty())
                || ("SUCCEEDED_WITH_GAPS".equals(status) && gapIds.isEmpty())) {
            throw new ApplicationProfileException("ENTRY_DISCOVERY_INVARIANT_BROKEN", "capability shard closure is invalid");
        }
    }

    private static List<String> sortedUnique(List<String> values, String prefix) {
        String prior = null;
        for (String value : values) {
            if (value == null
                    || !value.matches(prefix + ":[0-9a-f]{64}")
                    || (prior != null && prior.compareTo(value) >= 0)) {
                throw new ApplicationProfileException(
                        "ENTRY_DISCOVERY_INVARIANT_BROKEN", "capability shard IDs must be sorted and unique");
            }
            prior = value;
        }
        return List.copyOf(values);
    }
}
