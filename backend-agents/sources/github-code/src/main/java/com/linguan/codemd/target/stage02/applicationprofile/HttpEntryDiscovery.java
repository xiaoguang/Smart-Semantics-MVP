package com.linguan.codemd.target.stage02.applicationprofile;

import com.linguan.codemd.target.artifacts.ModulePublicationReference;
import java.util.List;

/** M2 result and its immediately persisted module artifact. */
public record HttpEntryDiscovery(
        String applicationProfileId,
        List<HttpEntryPoint> entries,
        List<CapabilitySiteV2> sites,
        List<CapabilityShardReceipt> shardReceipts,
        CapabilityDenominator denominator,
        ModulePublicationReference draftPublication) {
    public HttpEntryDiscovery {
        if (applicationProfileId == null || !applicationProfileId.matches("application-profile:[0-9a-f]{64}")
                || entries == null
                || sites == null
                || shardReceipts == null
                || denominator == null
                || draftPublication == null) {
            throw new ApplicationProfileException("STAGE02_REQUEST_INVALID", "HTTP discovery result is invalid");
        }
        entries = ordered(entries, HttpEntryPoint::entryId, "entry");
        sites = ordered(sites, CapabilitySiteV2::siteId, "site");
        shardReceipts = ordered(shardReceipts, CapabilityShardReceipt::shardId, "shard");
        if (denominator.candidateSites() != sites.size()) {
            throw new ApplicationProfileException("ENTRY_DISCOVERY_INVARIANT_BROKEN", "site denominator differs from site inventory");
        }
    }

    private static <T> List<T> ordered(
            List<T> values, java.util.function.Function<T, String> identifier, String subject) {
        String prior = null;
        for (T value : values) {
            String current = value == null ? null : identifier.apply(value);
            if (current == null || (prior != null && prior.compareTo(current) >= 0)) {
                throw new ApplicationProfileException(
                        "ENTRY_DISCOVERY_INVARIANT_BROKEN", subject + " IDs must be sorted and unique");
            }
            prior = current;
        }
        return List.copyOf(values);
    }
}
