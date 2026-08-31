package com.linguan.codemd.stage01;

import com.github.javaparser.ParserConfiguration;

import java.util.Map;
import java.util.Set;

/** Fixed M1/M2 capability profiles; callers may not invent a digest for a known profile ID. */
final class CapabilityProfileRegistry {
    private static final Profile JAVA17 = new Profile(
            "a8775d349a7b4e6f254691dd4f65d51833fbfc2bcbffe451508ec6d6ce78860a",
            ParserConfiguration.LanguageLevel.JAVA_17);
    private static final Profile JAVA8 = new Profile(
            "b908a3e8b769ccc2bd5f7b5642d55b8a166eba973d8f310abdf1c244b752c598",
            ParserConfiguration.LanguageLevel.JAVA_8);
    private static final Map<String, Profile> PROFILES = Map.of(
            "java17-springmvc-mybatis-static-v0", JAVA17,
            "java8-springmvc-mybatis-static-v0", JAVA8);
    /* Kept only for the pre-existing independent synthetic fixture contract. */
    private static final Set<String> LEGACY_SYNTHETIC_JAVA17_DIGESTS = Set.of(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

    private CapabilityProfileRegistry() {
    }

    static boolean accepts(CapabilityProfileRef reference) {
        if (reference == null) {
            return false;
        }
        Profile profile = PROFILES.get(reference.profileId());
        if (profile == null) {
            return false;
        }
        return profile.sha256().equals(reference.profileSha256())
                || (profile == JAVA17 && LEGACY_SYNTHETIC_JAVA17_DIGESTS.contains(reference.profileSha256()));
    }

    static ParserConfiguration.LanguageLevel parserLanguage(CapabilityProfileRef reference) {
        if (!accepts(reference)) {
            throw new Stage01Exception(Stage01FailureCode.PROFILE_REFERENCE_INVALID);
        }
        return PROFILES.get(reference.profileId()).languageLevel();
    }

    private record Profile(String sha256, ParserConfiguration.LanguageLevel languageLevel) {
    }
}
