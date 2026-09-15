package org.sourceanalysis.app.analysis.knowledge;

import java.util.Comparator;
import java.util.List;
import org.sourceanalysis.app.analysis.interpretation.material.SourceReference;

/** Deterministically renders the portable, human-readable view of saved source references. */
final class SourcesMarkdownRenderer {

  private SourcesMarkdownRenderer() {}

  static String render(List<SourceReference> sourceReferences) {
    StringBuilder markdown = new StringBuilder("# 来源索引\n\n");
    sourceReferences.stream()
        .sorted(Comparator.comparing(SourceReference::ref))
        .forEach(source -> appendSource(markdown, source));
    return markdown.toString();
  }

  private static void appendSource(StringBuilder markdown, SourceReference source) {
    String fence = fenceFor(source.snippet());
    markdown
        .append("<a id=\"")
        .append(anchorFor(source.ref()))
        .append("\"></a>\n")
        .append("## ")
        .append(source.ref())
        .append("\n\n")
        .append('`')
        .append(source.file())
        .append(':')
        .append(source.startLine())
        .append('–')
        .append(source.endLine())
        .append("`\n\n")
        .append(fence)
        .append("\n")
        .append(source.snippet())
        .append("\n")
        .append(fence)
        .append("\n\n");
  }

  static String anchorFor(String sourceRef) {
    return sourceRef.toLowerCase(java.util.Locale.ROOT);
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
