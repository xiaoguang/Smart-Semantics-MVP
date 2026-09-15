package org.sourceanalysis.app.analysis.knowledge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Deterministically renders the reviewed process catalog without calling a Provider. */
final class BusinessProcessMarkdownRenderer {

  private BusinessProcessMarkdownRenderer() {}

  static String render(RepositoryBusinessProcessCatalog catalog, ProcessCoverage coverage) {
    StringBuilder markdown = new StringBuilder("# 仓库业务过程\n\n## 业务目录\n\n");
    if (catalog.businessAreas().isEmpty()) {
      markdown.append("尚未识别出可发布的业务领域。\n\n");
    } else {
      catalog.businessAreas().stream()
          .sorted(Comparator.comparing(RepositoryBusinessProcessCatalog.BusinessArea::areaId))
          .forEach(
              area ->
                  markdown
                      .append("- **")
                      .append(area.name())
                      .append("**：")
                      .append(area.purpose())
                      .append('\n'));
      markdown.append('\n');
    }
    if (!catalog.aliases().isEmpty()) {
      markdown.append("业务术语别名：\n\n");
      catalog.aliases().stream()
          .sorted(
              Comparator.comparing(RepositoryBusinessProcessCatalog.BusinessAlias::canonicalName))
          .forEach(
              alias ->
                  markdown
                      .append("- **")
                      .append(alias.canonicalName())
                      .append("**：")
                      .append(String.join("、", alias.aliases()))
                      .append('\n'));
      markdown.append('\n');
    }

    Map<String, RepositoryBusinessProcessCatalog.ActivityUse> uses =
        catalog.processes().stream()
            .flatMap(process -> process.activityUses().stream())
            .collect(
                Collectors.toMap(
                    RepositoryBusinessProcessCatalog.ActivityUse::activityUseId,
                    Function.identity(),
                    (left, right) -> left));
    catalog.processes().stream()
        .sorted(Comparator.comparing(RepositoryBusinessProcessCatalog.BusinessProcess::processId))
        .forEach(process -> renderProcess(markdown, process, uses, catalog));

    markdown.append("## 支撑、独立、未归类与未处理范围\n\n");
    appendActivityIds(markdown, "独立活动", catalog.standaloneActivityIds());
    appendActivityIds(markdown, "未归类活动", catalog.unclassifiedActivityIds());
    List<String> supportIds =
        coverage.activityDispositions().stream()
            .filter(value -> "SUPPORT_ONLY".equals(value.disposition()))
            .map(ProcessCoverage.ActivityDisposition::activityId)
            .sorted()
            .toList();
    appendActivityIds(markdown, "支撑活动", supportIds);
    List<String> notProcessed =
        coverage.activityDispositions().stream()
            .filter(value -> "NOT_PROCESSED_CAPACITY".equals(value.disposition()))
            .map(ProcessCoverage.ActivityDisposition::activityId)
            .sorted()
            .toList();
    appendActivityIds(markdown, "未处理活动", notProcessed);
    markdown
        .append("- 覆盖状态：")
        .append(coverage.coverageStatus())
        .append("；语义交付状态：")
        .append(coverage.semanticDeliveryStatus())
        .append("。\n");
    return markdown.toString();
  }

  private static void renderProcess(
      StringBuilder markdown,
      RepositoryBusinessProcessCatalog.BusinessProcess process,
      Map<String, RepositoryBusinessProcessCatalog.ActivityUse> uses,
      RepositoryBusinessProcessCatalog catalog) {
    markdown.append("## ").append(process.name()).append("\n\n");
    section(markdown, "目的与适用范围");
    markdown.append(process.purpose()).append(" 适用范围：").append(process.scope()).append("\n\n");

    section(markdown, "参与者与对象");
    item(markdown, "参与者", process.participants());
    item(markdown, "业务对象", process.businessObjects());
    markdown.append('\n');

    section(markdown, "步骤与分支");
    process.stages().stream()
        .sorted(Comparator.comparingInt(RepositoryBusinessProcessCatalog.ProcessStage::order))
        .forEach(stage -> renderStage(markdown, stage, uses));
    if (!process.branches().isEmpty()) {
      markdown.append("\n分支与回退：\n\n");
      process.branches().forEach(value -> markdown.append("- ").append(value).append('\n'));
    }
    markdown.append('\n');

    section(markdown, "重要业务规则");
    if (process.businessRules().isEmpty()) {
      markdown.append("- 未从现有材料识别出可发布的具体业务规则。\n");
    } else {
      process.businessRules().forEach(rule -> renderRule(markdown, rule));
    }
    markdown.append('\n');

    section(markdown, "结束结果");
    bullets(markdown, process.endResults());
    markdown.append('\n');

    section(markdown, "支撑活动与相关过程");
    if (process.supportActivityUseIds().isEmpty()) {
      markdown.append("- 无单独列出的支撑活动。\n");
    } else {
      process
          .supportActivityUseIds()
          .forEach(
              id -> {
                RepositoryBusinessProcessCatalog.ActivityUse use = uses.get(id);
                markdown
                    .append("- ")
                    .append(use == null ? id : use.role() + "：" + use.variant())
                    .append('\n');
              });
    }
    catalog.processRelations().stream()
        .filter(
            relation ->
                process.processId().equals(relation.fromProcessId())
                    || process.processId().equals(relation.toProcessId()))
        .forEach(
            relation ->
                markdown
                    .append("- ")
                    .append(relation.relationType())
                    .append("：")
                    .append(relation.description())
                    .append(refs(relation.sourceRefs()))
                    .append('\n'));
    markdown.append('\n');

    section(markdown, "待确认联系");
    if (process.pendingConnections().isEmpty()) {
      markdown.append("- 无。\n");
    } else {
      bullets(markdown, process.pendingConnections());
    }
    markdown.append('\n');

    section(markdown, "来源引用");
    if (process.sourceRefs().isEmpty()) {
      markdown.append("- 无可用源码短引用。\n");
    } else {
      process.sourceRefs().stream()
          .distinct()
          .sorted()
          .forEach(ref -> markdown.append("- [").append(ref).append("]\n"));
    }
    markdown.append('\n');
  }

  private static void renderStage(
      StringBuilder markdown,
      RepositoryBusinessProcessCatalog.ProcessStage stage,
      Map<String, RepositoryBusinessProcessCatalog.ActivityUse> uses) {
    markdown.append(stage.order()).append(". **").append(stage.name()).append("**");
    List<String> variants =
        stage.activityUseIds().stream()
            .map(uses::get)
            .filter(java.util.Objects::nonNull)
            .map(RepositoryBusinessProcessCatalog.ActivityUse::variant)
            .distinct()
            .toList();
    if (!variants.isEmpty()) {
      markdown.append("（").append(String.join("、", variants)).append("）");
    }
    markdown.append(refs(stage.sourceRefs())).append('\n');
    appendStageField(markdown, "进入条件", stage.entryConditions());
    appendStageField(markdown, "动作", stage.actions());
    appendStageField(markdown, "状态变化", stage.stateChanges());
    appendStageField(markdown, "拒绝条件", stage.rejectionConditions());
    appendStageField(markdown, "结果", stage.outcomes());
    appendStageField(markdown, "后续转移", stage.transitions());
    markdown.append("   - 结论性质：").append(stage.certainty()).append('\n');
  }

  private static void renderRule(
      StringBuilder markdown, RepositoryBusinessProcessCatalog.BusinessRule rule) {
    markdown
        .append("- **")
        .append(rule.subject())
        .append("**：当")
        .append(rule.when())
        .append("时，")
        .append(rule.actionOrDecision());
    if (rule.otherwise() != null && !rule.otherwise().isBlank()) {
      markdown.append("；否则，").append(rule.otherwise());
    }
    markdown
        .append("。结果：")
        .append(rule.result())
        .append("（")
        .append(rule.certainty())
        .append("）")
        .append(refs(rule.sourceRefs()))
        .append('\n');
  }

  private static void appendStageField(StringBuilder markdown, String label, List<String> values) {
    if (!values.isEmpty()) {
      markdown
          .append("   - ")
          .append(label)
          .append("：")
          .append(String.join("；", values))
          .append('\n');
    }
  }

  private static void section(StringBuilder markdown, String title) {
    markdown.append("### ").append(title).append("\n\n");
  }

  private static void item(StringBuilder markdown, String label, List<String> values) {
    markdown
        .append("- ")
        .append(label)
        .append("：")
        .append(values.isEmpty() ? "未识别" : String.join("、", values))
        .append('\n');
  }

  private static void bullets(StringBuilder markdown, List<String> values) {
    if (values.isEmpty()) {
      markdown.append("- 无。\n");
    } else {
      values.forEach(value -> markdown.append("- ").append(value).append('\n'));
    }
  }

  private static void appendActivityIds(StringBuilder markdown, String label, List<String> values) {
    markdown.append("- ").append(label).append("：");
    markdown.append(values.isEmpty() ? "无" : String.join("、", values)).append("。\n");
  }

  private static String refs(List<String> refs) {
    if (refs.isEmpty()) {
      return "";
    }
    List<String> sorted = new ArrayList<>(new java.util.LinkedHashSet<>(refs));
    sorted.sort(String::compareTo);
    return " " + sorted.stream().map(ref -> "[" + ref + "]").collect(Collectors.joining(" "));
  }
}
