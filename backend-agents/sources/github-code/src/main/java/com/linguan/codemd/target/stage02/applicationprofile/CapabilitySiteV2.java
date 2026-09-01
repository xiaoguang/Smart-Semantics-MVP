package com.linguan.codemd.target.stage02.applicationprofile;

import com.linguan.codemd.target.contracts.SourceLocatorV1;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** One complete disposition of a discovered Stage02 capability site. */
public record CapabilitySiteV2(
        String siteId,
        CapabilitySiteKind kind,
        SourceLocatorV1 primaryLocator,
        List<String> affectedEntryIds,
        SignalDisposition disposition,
        String reasonCode,
        List<CapabilityEvidenceRefV2> evidenceRefs) {
    public CapabilitySiteV2 {
        if (siteId == null
                || !siteId.matches("site:[0-9a-f]{64}")
                || kind == null
                || primaryLocator == null
                || affectedEntryIds == null
                || disposition == null
                || evidenceRefs == null
                || evidenceRefs.isEmpty()) {
            throw new ApplicationProfileException("ENTRY_DISCOVERY_INVARIANT_BROKEN", "capability site fields are invalid");
        }
        if ((disposition == SignalDisposition.SUPPORTED) != (reasonCode == null)) {
            throw new ApplicationProfileException(
                    "ENTRY_DISCOVERY_INVARIANT_BROKEN", "site reason must match its disposition");
        }
        affectedEntryIds = sortedUniqueEntryIds(affectedEntryIds);
        evidenceRefs = sortedEvidence(evidenceRefs);
        boolean hasPrimary = evidenceRefs.stream()
                .filter(evidence -> evidence.kind() == CapabilityEvidenceRefV2.Kind.SOURCE_EXCERPT)
                .map(CapabilityEvidenceRefV2::sourceExcerpt)
                .anyMatch(excerpt -> primaryLocator.equals(excerpt.locator()));
        if (!hasPrimary) {
            throw new ApplicationProfileException(
                    "ENTRY_DISCOVERY_INVARIANT_BROKEN", "site evidence must contain its primary locator");
        }
    }

    private static List<String> sortedUniqueEntryIds(List<String> values) {
        String prior = null;
        for (String value : values) {
            if (value == null
                    || !value.matches("entry:[0-9a-f]{64}")
                    || (prior != null && prior.compareTo(value) >= 0)) {
                throw new ApplicationProfileException(
                        "ENTRY_DISCOVERY_INVARIANT_BROKEN", "affected entry IDs must be sorted and unique");
            }
            prior = value;
        }
        return List.copyOf(values);
    }

    private static List<CapabilityEvidenceRefV2> sortedEvidence(List<CapabilityEvidenceRefV2> values) {
        List<CapabilityEvidenceRefV2> ordered = new ArrayList<>(values);
        if (ordered.stream().anyMatch(value -> value == null)) {
            throw new ApplicationProfileException("ENTRY_DISCOVERY_INVARIANT_BROKEN", "site evidence cannot contain null");
        }
        ordered.sort(Comparator.comparing(CapabilitySiteV2::evidenceSortKey));
        return List.copyOf(ordered);
    }

    private static String evidenceSortKey(CapabilityEvidenceRefV2 evidence) {
        if (evidence.kind() == CapabilityEvidenceRefV2.Kind.SOURCE_EXCERPT) {
            SourceLocatorV1 locator = evidence.sourceExcerpt().locator();
            return "SOURCE_EXCERPT\n" + locator.path() + "\n" + locator.startByte() + "\n" + locator.endByteExclusive();
        }
        return "ARTIFACT_REFERENCE\n" + evidence.artifactEvidence().artifactRef().artifactId();
    }
}
