package com.linguan.codemd.target.artifacts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

final class FileSystemRunStoreHandle implements RunStoreHandle {
    private final Path root;
    private boolean closed;

    private FileSystemRunStoreHandle(Path root) {
        this.root = root;
    }

    static FileSystemRunStoreHandle open(Path root) throws IOException {
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new ArtifactStoreException("MODULE_PUBLICATION_INVALID", "store root must be a non-symlink directory");
        }
        return new FileSystemRunStoreHandle(root.toRealPath(LinkOption.NOFOLLOW_LINKS));
    }

    synchronized Path root() {
        if (closed) {
            throw new ArtifactStoreException("MODULE_PUBLICATION_INVALID", "run store handle is closed");
        }
        return root;
    }

    @Override
    public synchronized void close() {
        closed = true;
    }
}
