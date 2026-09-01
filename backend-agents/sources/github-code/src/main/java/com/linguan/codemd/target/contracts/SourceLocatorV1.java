package com.linguan.codemd.target.contracts;

import java.util.regex.Pattern;

/** Exact, repository-relative location of one continuous UTF-8 span in a frozen Stage01 file. */
public record SourceLocatorV1(
        String fileId,
        String path,
        long startByte,
        long endByteExclusive,
        int startLine,
        int startColumn,
        int endLine,
        int endColumn) {
    private static final Pattern FILE_ID = Pattern.compile("file:[0-9a-f]{64}");

    public SourceLocatorV1 {
        if (fileId == null || !FILE_ID.matcher(fileId).matches()) {
            throw new IllegalArgumentException("fileId must be an exact Stage01 file identity");
        }
        new SourceLocator(path, startByte, endByteExclusive, startLine, startColumn, endLine, endColumn);
    }
}
