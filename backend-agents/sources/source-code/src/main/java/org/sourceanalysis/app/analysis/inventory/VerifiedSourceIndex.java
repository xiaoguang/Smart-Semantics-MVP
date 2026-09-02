package org.sourceanalysis.app.analysis.inventory;

import java.util.List;

/**
 * The deterministic M2 result before module publication projects it to canonical JSON artifacts.
 */
public record VerifiedSourceIndex(
    String snapshotId,
    String requestIdentity,
    int verifiedRegularFileCount,
    int analyzableTextFileCount,
    int nonAnalyzableMediaFileCount,
    List<VerifiedSourceFile> verifiedFiles) {

  public VerifiedSourceIndex {
    if (snapshotId == null || !snapshotId.matches("snapshot:[0-9a-f]{64}")) {
      throw new IllegalArgumentException("snapshot ID must be canonical");
    }
    if (requestIdentity == null || !requestIdentity.matches("run-request:[0-9a-f]{64}")) {
      throw new IllegalArgumentException("request identity must be canonical");
    }
    verifiedFiles = List.copyOf(verifiedFiles);
    if (verifiedRegularFileCount != verifiedFiles.size() || verifiedRegularFileCount < 1) {
      throw new IllegalArgumentException("verified file count must match the file list");
    }
    int textCount =
        (int)
            verifiedFiles.stream()
                .filter(
                    file -> file.analysisDisposition() == SourceAnalysisDisposition.ANALYZABLE_TEXT)
                .count();
    int mediaCount = verifiedRegularFileCount - textCount;
    if (analyzableTextFileCount != textCount || nonAnalyzableMediaFileCount != mediaCount) {
      throw new IllegalArgumentException("verified source partitions must match the file list");
    }
  }
}
