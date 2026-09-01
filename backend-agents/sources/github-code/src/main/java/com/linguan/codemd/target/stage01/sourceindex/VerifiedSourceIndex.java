package com.linguan.codemd.target.stage01.sourceindex;

import com.linguan.codemd.target.stage01.capture.AnalysisDisposition;
import java.util.List;
import java.util.Set;

/** Complete, rootless M2 source index persisted before Stage01 publication. */
public record VerifiedSourceIndex(
        String snapshotId,
        String requestArtifactId,
        long verifiedRegularFileCount,
        long analyzableTextFileCount,
        long nonAnalyzableMediaFileCount,
        List<VerifiedSourceFile> verifiedFiles,
        List<SourceShardReceipt> shardReceipts,
        String sourceIntegrity) {
    public VerifiedSourceIndex {
        if (snapshotId == null || !snapshotId.matches("snapshot:[0-9a-f]{64}")
                || requestArtifactId == null || !requestArtifactId.matches("source-request:[0-9a-f]{64}")
                || verifiedRegularFileCount < 1 || analyzableTextFileCount < 0 || nonAnalyzableMediaFileCount < 0
                || !"VERIFIED".equals(sourceIntegrity)) {
            throw new SourceIndexException("SOURCE_HANDLE_INVALID", "verified source index header is invalid");
        }
        verifiedFiles = List.copyOf(verifiedFiles);
        shardReceipts = List.copyOf(shardReceipts);
        if (verifiedRegularFileCount != verifiedFiles.size()
                || verifiedRegularFileCount != analyzableTextFileCount + nonAnalyzableMediaFileCount
                || shardReceipts.isEmpty()) {
            throw new SourceIndexException("SOURCE_HANDLE_INVALID", "verified source index counts do not close");
        }
        String priorPath = null;
        for (VerifiedSourceFile file : verifiedFiles) {
            if (file == null || (priorPath != null && priorPath.compareTo(file.path()) >= 0)) {
                throw new SourceIndexException("SOURCE_HANDLE_INVALID", "verified files must be path-sorted and unique");
            }
            priorPath = file.path();
        }
        long text = verifiedFiles.stream()
                .filter(file -> file.analysisDisposition() == AnalysisDisposition.ANALYZABLE_TEXT)
                .count();
        if (text != analyzableTextFileCount) {
            throw new SourceIndexException("SOURCE_HANDLE_INVALID", "text count does not match verified files");
        }
        Set<String> expected = verifiedFiles.stream().map(VerifiedSourceFile::fileId).collect(java.util.stream.Collectors.toSet());
        Set<String> actual = shardReceipts.stream()
                .flatMap(receipt -> receipt.denominatorFileIds().stream())
                .collect(java.util.stream.Collectors.toSet());
        long declared = shardReceipts.stream().mapToLong(receipt -> receipt.denominatorFileIds().size()).sum();
        if (declared != actual.size() || !expected.equals(actual)) {
            throw new SourceIndexException("SOURCE_HANDLE_INVALID", "shard denominators do not close to verified files");
        }
    }
}
