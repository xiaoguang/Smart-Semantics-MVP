package org.sourceanalysis.app.analysis.knowledge;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;

/**
 * Read-only text access over one already verified source-text set.
 *
 * <p>This is deliberately an in-memory view of frozen bytes. It never resolves a host path,
 * reparses source, or falls back to a working tree.
 */
final class FrozenProcessSourceCorpus {

  private static final Comparator<String> UTF8_ORDER =
      (left, right) -> {
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        int length = Math.min(leftBytes.length, rightBytes.length);
        for (int index = 0; index < length; index++) {
          int compared = Integer.compareUnsigned(leftBytes[index], rightBytes[index]);
          if (compared != 0) {
            return compared;
          }
        }
        return Integer.compare(leftBytes.length, rightBytes.length);
      };

  private final List<SourceFile> files;
  private final Map<String, StoredFile> filesBySelector;

  FrozenProcessSourceCorpus(VerifiedSourceTextSet sourceTextSet) {
    if (sourceTextSet == null) {
      throw failure("PROCESS_SOURCE_TEXT_SET_INVALID");
    }

    List<VerifiedSourceTextDocument> documents =
        sourceTextSet.documents().stream()
            .sorted(Comparator.comparing(VerifiedSourceTextDocument::path, UTF8_ORDER))
            .toList();
    Map<String, StoredFile> bySelector = new TreeMap<>(UTF8_ORDER);
    List<SourceFile> directory = new ArrayList<>(documents.size());
    for (int index = 0; index < documents.size(); index++) {
      VerifiedSourceTextDocument document = documents.get(index);
      String fileKey = "F" + (index + 1);
      StoredFile stored = new StoredFile(document, fileKey);
      if (bySelector.put(document.path(), stored) != null
          || bySelector.put(fileKey, stored) != null) {
        throw failure("PROCESS_SOURCE_TEXT_DUPLICATE_FILE");
      }
      directory.add(
          new SourceFile(
              fileKey,
              document.path(),
              document.mediaType(),
              document.sizeBytes(),
              stored.lineCount()));
    }
    files = List.copyOf(directory);
    filesBySelector = Map.copyOf(bySelector);
  }

  List<SourceFile> files() {
    return files;
  }

  SourceText read(String fileSelector, ReadRange range) {
    StoredFile file = requiredFile(fileSelector);
    if (range == null) {
      throw failure("PROCESS_SOURCE_TEXT_RANGE_INVALID");
    }

    int startLine = range.wholeFile ? 1 : range.startLine;
    int endLine = range.wholeFile ? file.lineCount() : range.endLine;
    if (file.lineCount() == 0) {
      if (!range.wholeFile) {
        throw failure("PROCESS_SOURCE_TEXT_RANGE_OUT_OF_BOUNDS");
      }
      return new SourceText(file.path(), 1, 0, "", true);
    }
    if (startLine < 1 || endLine < startLine || endLine > file.lineCount()) {
      throw failure("PROCESS_SOURCE_TEXT_RANGE_OUT_OF_BOUNDS");
    }

    String text = file.text(startLine, endLine);
    return new SourceText(
        file.path(), startLine, endLine, text, startLine == 1 && endLine == file.lineCount());
  }

  List<SearchHit> search(List<String> fileSelectors, String literal, int contextLines) {
    if (fileSelectors == null || fileSelectors.isEmpty()) {
      throw failure("PROCESS_SOURCE_TEXT_FILE_SELECTION_INVALID");
    }
    if (literal == null || literal.isEmpty()) {
      throw failure("PROCESS_SOURCE_TEXT_LITERAL_INVALID");
    }
    if (contextLines < 0) {
      throw failure("PROCESS_SOURCE_TEXT_CONTEXT_INVALID");
    }

    LinkedHashSet<StoredFile> selected = new LinkedHashSet<>();
    for (String fileSelector : fileSelectors) {
      selected.add(requiredFile(fileSelector));
    }

    List<SearchHit> matches = new ArrayList<>();
    selected.stream()
        .sorted(Comparator.comparing(StoredFile::path, UTF8_ORDER))
        .forEach(file -> matches.addAll(file.matches(literal, contextLines)));
    return List.copyOf(matches);
  }

  private StoredFile requiredFile(String fileSelector) {
    if (fileSelector == null
        || fileSelector.isBlank()
        || fileSelector.startsWith("/")
        || fileSelector.contains("\\")
        || fileSelector.equals("..")
        || fileSelector.startsWith("../")
        || fileSelector.contains("/../")) {
      throw failure("PROCESS_SOURCE_TEXT_PATH_INVALID");
    }
    StoredFile file = filesBySelector.get(fileSelector);
    if (file == null) {
      throw failure("PROCESS_SOURCE_TEXT_UNKNOWN_FILE");
    }
    return file;
  }

  private static IllegalArgumentException failure(String code) {
    return new IllegalArgumentException(code);
  }

  record SourceFile(String fileKey, String path, String mediaType, long sizeBytes, int lineCount) {
    SourceFile {
      Objects.requireNonNull(fileKey, "file key");
      Objects.requireNonNull(path, "path");
      Objects.requireNonNull(mediaType, "media type");
      if (sizeBytes < 0 || lineCount < 0) {
        throw failure("PROCESS_SOURCE_TEXT_FILE_METADATA_INVALID");
      }
    }
  }

  record SourceText(String path, int startLine, int endLine, String text, boolean complete) {
    SourceText {
      Objects.requireNonNull(path, "path");
      Objects.requireNonNull(text, "text");
      if (startLine < 1 || endLine < 0 || endLine + 1 < startLine) {
        throw failure("PROCESS_SOURCE_TEXT_RESULT_INVALID");
      }
    }
  }

  record SearchHit(String path, int startLine, int endLine, String text) {
    SearchHit {
      Objects.requireNonNull(path, "path");
      Objects.requireNonNull(text, "text");
      if (startLine < 1 || endLine < startLine) {
        throw failure("PROCESS_SOURCE_TEXT_RESULT_INVALID");
      }
    }
  }

  static final class ReadRange {

    private static final ReadRange WHOLE_FILE = new ReadRange(0, 0, true);

    private final int startLine;
    private final int endLine;
    private final boolean wholeFile;

    private ReadRange(int startLine, int endLine, boolean wholeFile) {
      this.startLine = startLine;
      this.endLine = endLine;
      this.wholeFile = wholeFile;
    }

    static ReadRange of(int startLine, int endLine) {
      if (startLine < 1 || endLine < startLine) {
        throw failure("PROCESS_SOURCE_TEXT_RANGE_INVALID");
      }
      return new ReadRange(startLine, endLine, false);
    }

    static ReadRange wholeFile() {
      return WHOLE_FILE;
    }
  }

  private record StoredFile(
      VerifiedSourceTextDocument document, String fileKey, String text, List<Line> lines) {

    private StoredFile(VerifiedSourceTextDocument document, String fileKey) {
      this(
          document,
          fileKey,
          new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8),
          lines(new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8)));
    }

    private String path() {
      return document.path();
    }

    private int lineCount() {
      return lines.size();
    }

    private String text(int startLine, int endLine) {
      return text.substring(
          lines.get(startLine - 1).startOffset(), lines.get(endLine - 1).endOffset());
    }

    private List<SearchHit> matches(String literal, int contextLines) {
      List<SearchHit> matches = new ArrayList<>();
      int offset = 0;
      while (offset <= text.length() - literal.length()) {
        int matchStart = text.indexOf(literal, offset);
        if (matchStart < 0) {
          break;
        }
        int matchEndExclusive = matchStart + literal.length();
        int matchStartLine = lineAt(matchStart);
        int matchEndLine = lineAt(matchEndExclusive - 1);
        int startLine = Math.max(1, matchStartLine - contextLines);
        int endLine = Math.min(lineCount(), matchEndLine + contextLines);
        matches.add(new SearchHit(path(), startLine, endLine, text(startLine, endLine)));
        offset = matchStart + 1;
      }
      return matches;
    }

    private int lineAt(int offset) {
      for (int index = 0; index < lines.size(); index++) {
        Line line = lines.get(index);
        if (offset >= line.startOffset() && offset < line.endOffset()) {
          return index + 1;
        }
      }
      throw failure("PROCESS_SOURCE_TEXT_RESULT_INVALID");
    }

    private static List<Line> lines(String text) {
      List<Line> lines = new ArrayList<>();
      int startOffset = 0;
      int index = 0;
      while (index < text.length()) {
        char character = text.charAt(index);
        if (character == '\n') {
          lines.add(new Line(startOffset, index + 1));
          startOffset = index + 1;
        } else if (character == '\r') {
          int endOffset = index + 1;
          if (endOffset < text.length() && text.charAt(endOffset) == '\n') {
            endOffset++;
            index++;
          }
          lines.add(new Line(startOffset, endOffset));
          startOffset = endOffset;
        }
        index++;
      }
      if (startOffset < text.length()) {
        lines.add(new Line(startOffset, text.length()));
      }
      return List.copyOf(lines);
    }
  }

  private record Line(int startOffset, int endOffset) {}
}
