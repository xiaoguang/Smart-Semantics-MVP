package com.linguan.codemd.target.stage02.applicationprofile;

import com.linguan.codemd.target.artifacts.ArtifactReference;
import java.util.Comparator;
import java.util.List;

/** The persisted Stage02 M1 conclusion about static parsers that may be enabled for one frozen repository. */
public record ApplicationProfile(
        String applicationProfileId,
        String snapshotId,
        String inventoryScopeKind,
        boolean repositoryCompletionEligible,
        ApplicationLanguage language,
        Integer languageVersion,
        List<FrameworkSignal> frameworkSignals,
        List<ConfigSignal> configSignals,
        ArtifactReference capabilityProfileRef) {
    public ApplicationProfile {
        if (applicationProfileId == null || !applicationProfileId.matches("application-profile:[0-9a-f]{64}")
                || snapshotId == null || !snapshotId.matches("snapshot:[0-9a-f]{64}")
                || !("COMPLETE_CAPTURE".equals(inventoryScopeKind) || "BOUNDED_PATH_SET".equals(inventoryScopeKind))
                || language == null
                || (languageVersion != null && languageVersion < 1)
                || capabilityProfileRef == null) {
            throw new ApplicationProfileException("STAGE02_REQUEST_INVALID", "application profile fields are invalid");
        }
        frameworkSignals = sortedFrameworkSignals(frameworkSignals);
        configSignals = sortedConfigSignals(configSignals);
        if (repositoryCompletionEligible != "COMPLETE_CAPTURE".equals(inventoryScopeKind)) {
            throw new ApplicationProfileException(
                    "STAGE02_REQUEST_INVALID", "repository eligibility must agree with its Stage01 scope");
        }
    }

    private static List<FrameworkSignal> sortedFrameworkSignals(List<FrameworkSignal> signals) {
        if (signals == null || signals.stream().anyMatch(signal -> signal == null)) {
            throw new ApplicationProfileException("STAGE02_REQUEST_INVALID", "framework signals are required");
        }
        List<FrameworkSignal> copy = List.copyOf(signals);
        List<FrameworkSignal> sorted = copy.stream()
                .sorted(Comparator.comparing((FrameworkSignal signal) -> signal.kind().name())
                        .thenComparing(signal -> signal.sourceExcerpt().locator().path())
                        .thenComparingLong(signal -> signal.sourceExcerpt().locator().startByte()))
                .toList();
        if (!copy.equals(sorted)) {
            throw new ApplicationProfileException("STAGE02_REQUEST_INVALID", "framework signals must be canonical sorted");
        }
        return copy;
    }

    private static List<ConfigSignal> sortedConfigSignals(List<ConfigSignal> signals) {
        if (signals == null || signals.stream().anyMatch(signal -> signal == null)) {
            throw new ApplicationProfileException("STAGE02_REQUEST_INVALID", "config signals are required");
        }
        List<ConfigSignal> copy = List.copyOf(signals);
        List<ConfigSignal> sorted = copy.stream()
                .sorted(Comparator.comparing((ConfigSignal signal) -> signal.kind().name())
                        .thenComparing(signal -> signal.sourceExcerpt().locator().path())
                        .thenComparingLong(signal -> signal.sourceExcerpt().locator().startByte()))
                .toList();
        if (!copy.equals(sorted)) {
            throw new ApplicationProfileException("STAGE02_REQUEST_INVALID", "config signals must be canonical sorted");
        }
        return copy;
    }
}
