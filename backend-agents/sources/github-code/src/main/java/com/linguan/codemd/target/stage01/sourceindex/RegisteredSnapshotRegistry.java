package com.linguan.codemd.target.stage01.sourceindex;

import com.linguan.codemd.target.artifacts.ImmutableBytes;

/**
 * Private-composition boundary for bytes belonging to a registered frozen snapshot.
 *
 * <p>The public M2 domain receives only this identity-based boundary. Filesystem locations,
 * working-tree paths and Git object locations remain behind its implementation.
 */
public interface RegisteredSnapshotRegistry {
    RegisteredSnapshotHandle resolve(String sourceRegistrationId);

    interface RegisteredSnapshotHandle {
        String snapshotId();

        RegisteredSourceFileMetadata inspect(String fileId);

        ImmutableBytes read(String fileId);
    }
}
