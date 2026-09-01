package com.linguan.codemd.target.stage02.applicationprofile;

/** Fixed static Spring MVC annotation set and finite source budget for Stage02 M2. */
public record HttpEntryDiscoveryProfile(String profileVersion, int maxJavaFiles, int maxEntries) {
    public HttpEntryDiscoveryProfile {
        if (profileVersion == null || !profileVersion.matches("spring-mvc-v[0-9]+") || maxJavaFiles < 1 || maxEntries < 1) {
            throw new ApplicationProfileException("STAGE02_REQUEST_INVALID", "HTTP discovery profile is invalid");
        }
    }

    public static HttpEntryDiscoveryProfile springMvcV2() {
        return new HttpEntryDiscoveryProfile("spring-mvc-v2", 20_000, 100_000);
    }
}
