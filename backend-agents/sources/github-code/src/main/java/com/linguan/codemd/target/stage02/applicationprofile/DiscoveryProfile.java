package com.linguan.codemd.target.stage02.applicationprofile;

/** Versioned, finite parser budget for deterministic Java/Spring MVC/MyBatis capability discovery. */
public record DiscoveryProfile(String profileVersion, int maxInspectedTextFiles, int maxTextFileBytes) {
    public DiscoveryProfile {
        if (profileVersion == null || !profileVersion.matches("java-spring-mybatis-v[0-9]+")
                || maxInspectedTextFiles < 1
                || maxTextFileBytes < 1) {
            throw new ApplicationProfileException("STAGE02_REQUEST_INVALID", "discovery profile is invalid");
        }
    }

    public static DiscoveryProfile javaSpringMvcMyBatisV2() {
        return new DiscoveryProfile("java-spring-mybatis-v2", 20_000, 4_000_000);
    }
}
