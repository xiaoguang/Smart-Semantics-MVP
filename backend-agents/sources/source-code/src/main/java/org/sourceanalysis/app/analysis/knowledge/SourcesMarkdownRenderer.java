package org.sourceanalysis.app.analysis.knowledge;

import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import org.sourceanalysis.app.analysis.interpretation.material.SourceReference;

/** Deterministically renders the portable, human-readable view of saved source references. */
final class SourcesMarkdownRenderer {

  private static final Pattern SOURCE_REFERENCE = Pattern.compile("S[0-9]+");

  private SourcesMarkdownRenderer() {}

  static String render(List<SourceReference> sourceReferences) {
    StringBuilder markdown = new StringBuilder("# 来源索引\n\n");
    sourceReferences.stream()
        .sorted(Comparator.comparing(SourceReference::ref))
        .forEach(source -> appendSource(markdown, source));
    return markdown.toString();
  }

  private static void appendSource(StringBuilder markdown, SourceReference source) {
    requireSafeReference(source.ref());
    String fence = fenceFor(source.snippet());
    markdown
        .append("<a id=\"")
        .append(anchorFor(source.ref()))
        .append("\"></a>\n")
        .append("## ")
        .append(source.ref())
        .append("\n\n")
        .append("文件：")
        .append(locationFor(source))
        .append("\n\n")
        .append(fence)
        .append("\n")
        .append(source.snippet())
        .append("\n")
        .append(fence)
        .append("\n\n");
  }

  static String anchorFor(String sourceRef) {
    requireSafeReference(sourceRef);
    return sourceRef.toLowerCase(java.util.Locale.ROOT);
  }

  static void requireSafeReference(String sourceRef) {
    if (sourceRef == null || !SOURCE_REFERENCE.matcher(sourceRef).matches()) {
      throw new IllegalArgumentException("BUSINESS_PROCESS_SOURCE_REFERENCE_INVALID");
    }
  }

  static String locationFor(SourceReference source) {
    return safeFile(source.file()) + ':' + source.startLine() + '–' + source.endLine();
  }

  private static String safeFile(String file) {
    String normalized = file.replace("\r", "\\r").replace("\n", "\\n");
    return normalized.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private static String fenceFor(String snippet) {
    int longestBacktickRun = 0;
    int currentRun = 0;
    for (int index = 0; index < snippet.length(); index++) {
      if (snippet.charAt(index) == '`') {
        currentRun++;
        longestBacktickRun = Math.max(longestBacktickRun, currentRun);
      } else {
        currentRun = 0;
      }
    }
    return "`".repeat(Math.max(3, longestBacktickRun + 1));
  }
}
