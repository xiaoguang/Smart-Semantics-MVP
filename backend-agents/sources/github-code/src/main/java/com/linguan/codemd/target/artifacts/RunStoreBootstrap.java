package com.linguan.codemd.target.artifacts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** The only bootstrap boundary that accepts a private filesystem root. */
public final class RunStoreBootstrap {
    private RunStoreBootstrap() {}

    public static RunStoreHandle open(Path configuredStoreRoot) {
        if (configuredStoreRoot == null) {
            throw new ArtifactStoreException("MODULE_PUBLICATION_INVALID", "configured store root is required");
        }
        try {
            Files.createDirectories(configuredStoreRoot);
            return FileSystemRunStoreHandle.open(configuredStoreRoot);
        } catch (IOException exception) {
            throw new ArtifactStoreException("MODULE_PUBLICATION_INVALID", "cannot open configured store root", exception);
        }
    }

    public static RunStoreHandle openForTest(Path emptyTemporaryDirectory) {
        if (emptyTemporaryDirectory == null) {
            throw new ArtifactStoreException("MODULE_PUBLICATION_INVALID", "test store root is required");
        }
        try {
            if (Files.isSymbolicLink(emptyTemporaryDirectory)
                    || !Files.isDirectory(emptyTemporaryDirectory, LinkOption.NOFOLLOW_LINKS)
                    || Files.list(emptyTemporaryDirectory).findAny().isPresent()) {
                throw new ArtifactStoreException(
                        "MODULE_PUBLICATION_INVALID", "test store root must be an empty non-symlink directory");
            }
            return FileSystemRunStoreHandle.open(emptyTemporaryDirectory);
        } catch (IOException exception) {
            throw new ArtifactStoreException("MODULE_PUBLICATION_INVALID", "cannot inspect test store root", exception);
        }
    }
}
