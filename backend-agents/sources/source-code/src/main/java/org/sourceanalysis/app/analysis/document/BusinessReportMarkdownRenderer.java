package org.sourceanalysis.app.analysis.document;

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
        markdown.append("完整源码依据保存在 source-refs.jsonl；正文中的短引用可用于查询。\n\n");
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
            .map(ref -> "[" + ref + "]")
            .reduce((left, right) -> left + "、" + right)
            .orElseThrow();
    return content.text() + "（" + citations + "）";
  }
}
