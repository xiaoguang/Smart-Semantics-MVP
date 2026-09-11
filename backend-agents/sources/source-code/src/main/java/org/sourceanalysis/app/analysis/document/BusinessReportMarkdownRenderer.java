package org.sourceanalysis.app.analysis.document;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.analysis.interpretation.material.SourceReference;

/** Deterministically turns approved report data into the reader-facing Markdown document. */
final class BusinessReportMarkdownRenderer {

  private BusinessReportMarkdownRenderer() {}

  static String render(BusinessReport report, List<SourceReference> sources) {
    Objects.requireNonNull(report, "business report");
    Objects.requireNonNull(sources, "source references");
    StringBuilder markdown = new StringBuilder("# ").append(report.title()).append("\n\n");
    for (BusinessReportSection section : report.sections()) {
      markdown
          .append("## ")
          .append(section.number())
          .append(". ")
          .append(section.title())
          .append("\n\n");
      section
          .paragraphs()
          .forEach(content -> markdown.append(renderContent(content)).append("\n\n"));
      section
          .items()
          .forEach(content -> markdown.append("- ").append(renderContent(content)).append("\n"));
      if (!section.items().isEmpty()) {
        markdown.append('\n');
      }
      if (section.number() == 1) {
        renderSourceDetails(markdown, sources);
      }
    }
    return markdown.toString();
  }

  private static String renderContent(BusinessReportContent content) {
    if (content.refs().isEmpty()) {
      return content.text();
    }
    String citations =
        content.refs().stream()
            .map(ref -> "[" + ref + "](#source-ref-" + anchor(ref) + ")")
            .reduce((left, right) -> left + "、" + right)
            .orElseThrow();
    return content.text() + "（" + citations + "）";
  }

  private static void renderSourceDetails(StringBuilder markdown, List<SourceReference> sources) {
    markdown.append("<details>\n<summary>技术依据（可选）</summary>\n\n");
    sources.stream()
        .sorted(Comparator.comparing(SourceReference::ref))
        .forEach(
            source ->
                markdown
                    .append("<a id=\"source-ref-")
                    .append(anchor(source.ref()))
                    .append("\"></a>\n- ")
                    .append(source.ref())
                    .append(" — ")
                    .append(source.file())
                    .append(":")
                    .append(source.startLine())
                    .append("–")
                    .append(source.endLine())
                    .append("\n  <pre><code>")
                    .append(escapeHtml(source.snippet()))
                    .append("</code></pre>\n\n"));
    markdown.append("</details>\n\n");
  }

  private static String anchor(String ref) {
    return ref.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
  }

  private static String escapeHtml(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
