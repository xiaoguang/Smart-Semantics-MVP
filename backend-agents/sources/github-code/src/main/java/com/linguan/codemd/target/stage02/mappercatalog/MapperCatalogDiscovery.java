package com.linguan.codemd.target.stage02.mappercatalog;

import com.linguan.codemd.target.artifacts.ModulePublicationReference;
import com.linguan.codemd.target.stage02.applicationprofile.CapabilitySiteV2;
import com.linguan.codemd.target.stage02.applicationprofile.CapabilityDenominator;
import com.linguan.codemd.target.stage02.applicationprofile.CapabilityShardReceipt;
import java.util.List;

/** M3's persisted catalog projection, before M4 publishes Stage02's public files. */
public record MapperCatalogDiscovery(
        String applicationProfileId,
        List<MapperCatalogEntry> entries,
        List<CapabilitySiteV2> sites,
        List<CapabilityShardReceipt> shardReceipts,
        CapabilityDenominator denominator,
        ModulePublicationReference draftPublication) {
    public MapperCatalogDiscovery {
        if (applicationProfileId == null
                || !applicationProfileId.matches("application-profile:[0-9a-f]{64}")
                || entries == null
                || sites == null
                || shardReceipts == null
                || denominator == null
                || draftPublication == null) {
            throw new IllegalArgumentException("mapper catalog discovery is invalid");
        }
        entries = List.copyOf(entries);
        sites = List.copyOf(sites);
        shardReceipts = List.copyOf(shardReceipts);
    }
}
