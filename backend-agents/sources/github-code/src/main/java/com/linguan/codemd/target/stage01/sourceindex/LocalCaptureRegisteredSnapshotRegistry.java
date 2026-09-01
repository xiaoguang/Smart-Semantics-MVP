package com.linguan.codemd.target.stage01.sourceindex;

import com.linguan.codemd.target.artifacts.ImmutableBytes;
import com.linguan.codemd.target.stage01.capture.FileSystemLocalGitCaptureStore;
import com.linguan.codemd.target.stage01.capture.LocalGitCaptureResult;
import com.linguan.codemd.target.stage01.capture.LocalGitSnapshotEntry;
import com.linguan.codemd.target.stage01.requestadmission.AdmittedSourceFile;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Bridges the private Capture store into M2's identity-only registered snapshot boundary. */
public final class LocalCaptureRegisteredSnapshotRegistry implements RegisteredSnapshotRegistry {
    private final FileSystemLocalGitCaptureStore captureStore;

    public LocalCaptureRegisteredSnapshotRegistry(FileSystemLocalGitCaptureStore captureStore) {
        this.captureStore = Objects.requireNonNull(captureStore, "captureStore");
    }

    @Override
    public RegisteredSnapshotHandle resolve(String sourceRegistrationId) {
        LocalGitCaptureResult result = captureStore.reopen(sourceRegistrationId);
        Map<String, LocalGitSnapshotEntry> entries = result.snapshotManifest().stream().collect(Collectors.toUnmodifiableMap(
                entry -> AdmittedSourceFile.fromCapture(entry).fileId(), entry -> entry));
        return new RegisteredSnapshotHandle() {
            @Override
            public String snapshotId() {
                return result.registration().snapshotId();
            }

            @Override
            public RegisteredSourceFileMetadata inspect(String fileId) {
                LocalGitSnapshotEntry entry = requiredEntry(fileId);
                return new RegisteredSourceFileMetadata(entry.sizeBytes(), entry.sha256(), true);
            }

            @Override
            public ImmutableBytes read(String fileId) {
                LocalGitSnapshotEntry entry = requiredEntry(fileId);
                return captureStore.readCapturedBlob(sourceRegistrationId, entry.sha256());
            }

            private LocalGitSnapshotEntry requiredEntry(String fileId) {
                LocalGitSnapshotEntry entry = entries.get(fileId);
                if (entry == null) {
                    throw new SourceIndexException("SOURCE_HANDLE_INVALID", "registered snapshot does not contain file identity");
                }
                return entry;
            }
        };
    }
}
