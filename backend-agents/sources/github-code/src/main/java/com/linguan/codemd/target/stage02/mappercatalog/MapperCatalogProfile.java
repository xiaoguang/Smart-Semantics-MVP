package com.linguan.codemd.target.stage02.mappercatalog;

/** Versioned static limits for the Stage02 MyBatis candidate cataloger. */
public record MapperCatalogProfile(String profileVersion, int maxJavaFiles, int maxXmlFiles) {
    public MapperCatalogProfile {
        if (profileVersion == null
                || !profileVersion.matches("mybatis-v[0-9]+")
                || maxJavaFiles < 1
                || maxXmlFiles < 1) {
            throw new IllegalArgumentException("mapper catalog profile is invalid");
        }
    }

    public static MapperCatalogProfile mybatisV2() {
        return new MapperCatalogProfile("mybatis-v2", 20_000, 100_000);
    }
}
