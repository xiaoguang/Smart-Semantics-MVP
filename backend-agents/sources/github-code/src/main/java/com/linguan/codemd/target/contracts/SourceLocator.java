package com.linguan.codemd.target.contracts;

/** Root-independent UTF-8 source span with exclusive byte and position ends. */
public record SourceLocator(
        String path,
        long startByteOffset,
        long endByteOffset,
        int startLine,
        int startColumn,
        int endLine,
        int endColumn) {

    public SourceLocator {
        path = requireRepositoryRelativePath(path);
        if (startByteOffset < 0 || endByteOffset <= startByteOffset) {
            throw new IllegalArgumentException("source byte offsets must be ordered with an exclusive end");
        }
        if (startLine < 1 || startColumn < 1 || endLine < 1 || endColumn < 1) {
            throw new IllegalArgumentException("source line and column coordinates are 1-based");
        }
        if (endLine < startLine || (endLine == startLine && endColumn <= startColumn)) {
            throw new IllegalArgumentException("source positions must be ordered with an exclusive end");
        }
    }

    private static String requireRepositoryRelativePath(String value) {
        CanonicalJson.requireCanonicalText(value, "path");
        if (value.startsWith("/") || value.indexOf('\\') >= 0 || isWindowsAbsolute(value)) {
            throw new IllegalArgumentException("source path must be repository-relative and slash-separated");
        }
        String[] segments = value.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("source path must not contain traversal segments");
            }
        }
        return value;
    }

    private static boolean isWindowsAbsolute(String value) {
        return value.length() >= 3
                && Character.isLetter(value.charAt(0))
                && value.charAt(1) == ':'
                && value.charAt(2) == '/';
    }
}
