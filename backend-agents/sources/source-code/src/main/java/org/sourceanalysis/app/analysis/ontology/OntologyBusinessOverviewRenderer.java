package org.sourceanalysis.app.analysis.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Offline, deterministic HTML projection of the four already-published ontology files. */
public final class OntologyBusinessOverviewRenderer {
  private static final String MERMAID_RESOURCE =
      "/org/sourceanalysis/app/analysis/ontology/vendor/mermaid-11.12.0/mermaid.min.js";

  @FunctionalInterface
  public interface MermaidBundleReader {
    ImmutableBytes read();
  }

  private final MermaidBundleReader bundleReader;
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  public OntologyBusinessOverviewRenderer() {
    this(OntologyBusinessOverviewRenderer::bundledMermaid);
  }

  public OntologyBusinessOverviewRenderer(MermaidBundleReader bundleReader) {
    this.bundleReader = Objects.requireNonNull(bundleReader, "offline Mermaid bundle reader");
  }

  public String render(
      ImmutableBytes ontology,
      ImmutableBytes coverage,
      ImmutableBytes sourceIndex,
      ImmutableBytes review) {
    Objects.requireNonNull(ontology, "published ontology");
    Objects.requireNonNull(coverage, "published coverage");
    Objects.requireNonNull(sourceIndex, "published source index");
    Objects.requireNonNull(review, "published review");
    JsonNode ontologyDocument = json.parseCanonical(ontology);
    JsonNode coverageDocument = json.parseCanonical(coverage);
    JsonNode reviewDocument = json.parseCanonical(review);
    if (!"ontology-v2".equals(ontologyDocument.path("schemaVersion").asText())
        || !(Set.of("v3", "v4", "v5").stream()
            .anyMatch(
                version ->
                    ("ontology-coverage-" + version)
                            .equals(coverageDocument.path("schemaVersion").asText())
                        && ("ontology-review-" + version)
                            .equals(reviewDocument.path("schemaVersion").asText())))
        || !ontologyDocument.path("objectTypes").isArray()
        || !ontologyDocument.path("linkTypes").isArray()) {
      throw invalid();
    }
    Map<String, JsonNode> sources = sourceRows(sourceIndex);
    ImmutableBytes bundle = bundleReader.read();
    if (bundle == null || bundle.size() == 0) {
      throw new IllegalStateException("ONTOLOGY_OVERVIEW_BUNDLE_MISSING");
    }
    List<JsonNode> objects = sorted(ontologyDocument.path("objectTypes"));
    List<JsonNode> links = sorted(ontologyDocument.path("linkTypes"));
    Map<String, String> nodeIds = graphNodeIds(objects);
    List<JsonNode> graphObjects =
        objects.stream()
            .filter(object -> canonicalRef(object).equals(object.path("globalId").asText()))
            .toList();
    String graph = graph(graphObjects, links, nodeIds, reviewDocument);
    boolean localView = "ontology-review-v5".equals(reviewDocument.path("schemaVersion").asText());
    List<List<JsonNode>> localCandidates =
        localView
            ? localLinkCandidates(ontologyDocument, links, nodeIds, reviewDocument)
            : List.of();
    List<JsonNode> localLinks = localCandidates.isEmpty() ? List.of() : localCandidates.get(0);
    Set<String> localNodeIds = new HashSet<>();
    for (JsonNode link : localLinks) {
      localNodeIds.add(nodeIds.get(link.path("fromObjectRef").asText()));
      localNodeIds.add(nodeIds.get(link.path("toObjectRef").asText()));
    }
    List<JsonNode> localObjects =
        graphObjects.stream()
            .filter(object -> localNodeIds.contains(nodeIds.get(object.path("globalId").asText())))
            .toList();
    StringBuilder html = new StringBuilder(4096);
    html.append("<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">")
        .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        .append("<title>业务本体概览</title><style>")
        .append(
            "body{font:15px system-ui,sans-serif;max-width:1100px;margin:2rem auto;padding:0 1rem;color:#17212b}")
        .append(".note{background:#f4f7fa;padding:.8rem;border-left:4px solid #58708a}")
        .append(".mermaid{overflow:auto;background:#fff;border:1px solid #d9e1e8;padding:1rem}")
        .append("details{border-bottom:1px solid #d9e1e8;padding:.6rem 0}summary{cursor:pointer}")
        .append("code,pre{white-space:pre-wrap;overflow-wrap:anywhere}li{margin:.25rem 0}")
        .append("</style></head><body><h1>业务本体概览</h1>")
        .append("<p class=\"note\">仅投影已发布四文件；模型审阅不等于人工验收，实线也不表示机器证明正确。箭头是原关系引用方向，不是办理顺序。</p>");
    if (localView) {
      html.append("<h2>局部业务联系</h2><p>局部图：")
          .append(localObjects.size())
          .append("个对象、")
          .append(localLinks.size())
          .append("条已有联系</p>");
      html.append("<details><summary>局部视图候选：")
          .append(localCandidates.size())
          .append("</summary><ol>");
      for (List<JsonNode> candidate : localCandidates) {
        html.append("<li>");
        for (JsonNode link : candidate) {
          html.append("<code>")
              .append(escapeHtml(link.path("globalId").asText()))
              .append("</code> ")
              .append(escapeHtml(link.path("name").asText()))
              .append("；");
        }
        html.append("</li>");
      }
      html.append("</ol></details>");
      if (localLinks.isEmpty()) html.append("<p>没有经过已审类型对应节点的跨段连续两边。</p>");
      html.append("<pre class=\"mermaid\" id=\"ontology-business-graph\">")
          .append(escapeHtml(localGraph(localObjects, localLinks, nodeIds, reviewDocument)))
          .append("</pre>");
      html.append("<details><summary>完整图：")
          .append(graphObjects.size())
          .append("个对象、")
          .append(links.size())
          .append("条联系；包含其余分支及孤立对象</summary>")
          .append("<pre class=\"mermaid\" id=\"ontology-all-graph\">")
          .append(escapeHtml(graph))
          .append("</pre></details>");
    } else {
      html.append("<pre class=\"mermaid\" id=\"ontology-business-graph\">")
          .append(escapeHtml(graph))
          .append("</pre>");
    }
    appendDefinitions(html, "对象", objects, sources);
    appendDefinitions(html, "关系", links, sources);
    for (String field : List.of("operations", "rules", "dimensions", "measures", "metrics")) {
      appendDefinitions(html, field, sorted(ontologyDocument.path(field)), sources);
    }
    appendSources(html, sources);
    appendLimitations(html, coverageDocument, reviewDocument);
    html.append("<details><summary>完整审阅记录</summary><pre>")
        .append(escapeHtml(utf8(review)))
        .append("</pre></details>");
    html.append("<script src=\"data:text/javascript;base64,")
        .append(Base64.getEncoder().encodeToString(bundle.copyToByteArray()))
        .append("\"></script>")
        .append(
            "<script>mermaid.initialize({securityLevel: 'strict', htmlLabels: true, startOnLoad: false});")
        .append("mermaid.run({querySelector: '.mermaid'});</script>")
        .append("</body></html>");
    return html.toString();
  }

  /** Selects, never invents, a directed cross-task pair through a reviewed type-equivalent node. */
  private static List<List<JsonNode>> localLinkCandidates(
      JsonNode ontology, List<JsonNode> links, Map<String, String> nodeIds, JsonNode review) {
    List<List<JsonNode>> candidates = new ArrayList<>();
    Set<String> middleNodes = new HashSet<>();
    for (JsonNode decision : review.path("identityDecisions")) {
      if ("SAME_OBJECT_TYPE_NOT_INSTANCE_IDENTITY"
          .equals(decision.path("equivalenceSemantics").asText())) {
        String middle = nodeIds.get(decision.path("resolvedCanonicalObjectRef").asText());
        if (middle != null) middleNodes.add(middle);
      }
    }
    Map<String, String> producers = new LinkedHashMap<>();
    for (JsonNode index : ontology.path("definitionIndex"))
      producers.put(index.path("globalId").asText(), index.path("producingTaskId").asText());
    for (JsonNode first : links)
      for (JsonNode second : links) {
        String from = nodeIds.get(first.path("fromObjectRef").asText());
        String middle = nodeIds.get(first.path("toObjectRef").asText());
        String next = nodeIds.get(second.path("fromObjectRef").asText());
        String to = nodeIds.get(second.path("toObjectRef").asText());
        String firstTask = producers.get(first.path("globalId").asText());
        String secondTask = producers.get(second.path("globalId").asText());
        if (middle != null
            && middle.equals(next)
            && middleNodes.contains(middle)
            && from != null
            && to != null
            && !from.equals(middle)
            && !to.equals(middle)
            && !from.equals(to)
            && firstTask != null
            && !firstTask.isBlank()
            && secondTask != null
            && !secondTask.isBlank()
            && !firstTask.equals(secondTask)) candidates.add(List.of(first, second));
      }
    return List.copyOf(candidates);
  }

  private static String localGraph(
      List<JsonNode> objects, List<JsonNode> links, Map<String, String> nodeIds, JsonNode review) {
    StringBuilder graph = new StringBuilder("flowchart TB\n");
    for (JsonNode object : objects)
      graph
          .append("  ")
          .append(nodeIds.get(object.path("globalId").asText()))
          .append("[\"")
          .append(graphLabel(object.path("name").asText()))
          .append("\"]\n");
    Set<String> disputed = new HashSet<>();
    for (JsonNode issue : review.path("assemblyIssues"))
      issue.path("definitionRefs").forEach(ref -> disputed.add(ref.asText()));
    for (JsonNode link : links) {
      boolean solid =
          "CONFIRMED".equals(link.path("certainty").asText())
              && !disputed.contains(link.path("globalId").asText());
      String label = link.path("name").asText();
      if (label.isBlank()) label = "机制待确认";
      graph
          .append("  ")
          .append(nodeIds.get(link.path("fromObjectRef").asText()))
          .append(solid ? " -->" : " -.->")
          .append("|\"")
          .append(graphLabel(label))
          .append("\"| ")
          .append(nodeIds.get(link.path("toObjectRef").asText()))
          .append("\n");
    }
    return graph.toString();
  }

  private static ImmutableBytes bundledMermaid() {
    try (InputStream stream =
        OntologyBusinessOverviewRenderer.class.getResourceAsStream(MERMAID_RESOURCE)) {
      if (stream == null) throw new IllegalStateException("ONTOLOGY_OVERVIEW_BUNDLE_MISSING");
      return ImmutableBytes.copyOf(stream.readAllBytes());
    } catch (IOException error) {
      throw new IllegalStateException("ONTOLOGY_OVERVIEW_BUNDLE_MISSING", error);
    }
  }

  private Map<String, JsonNode> sourceRows(ImmutableBytes sourceIndex) {
    Map<String, JsonNode> rows = new LinkedHashMap<>();
    for (String line : utf8(sourceIndex).split("\\R")) {
      if (line.isBlank()) continue;
      JsonNode row =
          json.parseCanonical(ImmutableBytes.copyOf(line.getBytes(StandardCharsets.UTF_8)));
      String ref = row.path("sourceRef").asText();
      if (!"ontology-source-v1".equals(row.path("schemaVersion").asText())
          || ref.isBlank()
          || rows.putIfAbsent(ref, row) != null) {
        throw invalid();
      }
    }
    return rows;
  }

  private static String graph(
      List<JsonNode> objects, List<JsonNode> links, Map<String, String> nodeIds, JsonNode review) {
    StringBuilder graph = new StringBuilder("flowchart LR\n");
    for (String role : List.of("MAIN", "SUPPORT", "TECHNICAL_OR_UNKNOWN")) {
      String title =
          switch (role) {
            case "MAIN" -> "主业务对象";
            case "SUPPORT" -> "支撑对象";
            default -> "待确认对象";
          };
      graph.append("  subgraph ").append(role).append("[\"").append(title).append("\"]\n");
      for (JsonNode object : objects) {
        if (!role.equals(object.path("displayRole").asText())) continue;
        graph
            .append("    ")
            .append(nodeIds.get(object.path("globalId").asText()))
            .append("[\"")
            .append(graphLabel(object.path("name").asText()))
            .append("\"]\n");
      }
      graph.append("  end\n");
    }
    Set<String> disputed = new HashSet<>();
    for (JsonNode issue : review.path("assemblyIssues")) {
      issue.path("definitionRefs").forEach(ref -> disputed.add(ref.asText()));
    }
    for (JsonNode link : links) {
      String from = nodeIds.get(link.path("fromObjectRef").asText());
      String to = nodeIds.get(link.path("toObjectRef").asText());
      if (from == null || to == null || link.path("globalId").asText().isBlank()) throw invalid();
      boolean solid =
          "CONFIRMED".equals(link.path("certainty").asText())
              && !disputed.contains(link.path("globalId").asText());
      List<String> mechanisms = new ArrayList<>();
      int index = 0;
      for (JsonNode item : link.path("mechanism")) {
        index++;
        String description = item.path("description").asText();
        mechanisms.add(description.isBlank() ? "机制项" + index + "描述未提供" : description);
      }
      String mechanism = String.join("；", mechanisms);
      if (mechanism.isBlank()) mechanism = link.path("name").asText();
      if (mechanism.isBlank()) mechanism = "机制待确认";
      graph
          .append("  ")
          .append(from)
          .append(solid ? " -->" : " -.->")
          .append("|\"")
          .append(graphLabel(mechanism))
          .append("\"| ")
          .append(to)
          .append("\n");
    }
    return graph.toString();
  }

  private static Map<String, String> graphNodeIds(List<JsonNode> objects) {
    Map<String, JsonNode> definitions = new LinkedHashMap<>();
    for (JsonNode object : objects) {
      String ref = object.path("globalId").asText();
      if (ref.isBlank()
          || definitions.putIfAbsent(ref, object) != null
          || !Set.of("MAIN", "SUPPORT", "TECHNICAL_OR_UNKNOWN")
              .contains(object.path("displayRole").asText())) {
        throw invalid();
      }
    }
    Map<String, String> canonicalNodes = new java.util.TreeMap<>();
    for (JsonNode object : objects) {
      String canonical = canonicalRef(object);
      JsonNode target = definitions.get(canonical);
      if (target == null || !canonical.equals(canonicalRef(target))) throw invalid();
      canonicalNodes.putIfAbsent(canonical, "");
    }
    int index = 0;
    for (String canonical : canonicalNodes.keySet()) {
      canonicalNodes.put(canonical, "n" + (++index));
    }
    Map<String, String> result = new LinkedHashMap<>();
    for (JsonNode object : objects) {
      result.put(object.path("globalId").asText(), canonicalNodes.get(canonicalRef(object)));
    }
    return result;
  }

  private static String canonicalRef(JsonNode object) {
    return object.path("canonicalObjectRef").asText(object.path("globalId").asText());
  }

  private static void appendDefinitions(
      StringBuilder html, String title, List<JsonNode> definitions, Map<String, JsonNode> sources) {
    html.append("<h2>")
        .append(escapeHtml(title))
        .append("（")
        .append(definitions.size())
        .append("）</h2>");
    for (JsonNode definition : definitions) {
      html.append("<details><summary>")
          .append(escapeHtml(definition.path("name").asText()))
          .append(" · ")
          .append(escapeHtml(definition.path("certainty").asText()));
      if (definition.has("displayRole"))
        html.append(" · ").append(escapeHtml(definition.path("displayRole").asText()));
      html.append("</summary><p>")
          .append(escapeHtml(definition.path("definition").asText()))
          .append("</p>")
          .append("<p>定义引用：<code>")
          .append(escapeHtml(definition.path("globalId").asText()))
          .append("</code></p>");
      if (definition.has("fromObjectRef")) {
        html.append("<p>方向：<code>")
            .append(escapeHtml(definition.path("fromObjectRef").asText()))
            .append(" → ")
            .append(escapeHtml(definition.path("toObjectRef").asText()))
            .append("</code></p>");
      }
      if (definition.path("mechanism").isArray() && !definition.path("mechanism").isEmpty()) {
        html.append("<p>完整机制：</p><ol>");
        for (JsonNode mechanism : definition.path("mechanism")) {
          html.append("<li><pre>").append(escapeHtml(mechanism.toString())).append("</pre></li>");
        }
        html.append("</ol>");
      }
      html.append("<p>依据：</p><ul>");
      for (JsonNode ref : definition.path("evidenceRefs")) {
        JsonNode source = sources.get(ref.asText());
        html.append("<li><code>").append(escapeHtml(ref.asText())).append("</code>");
        if (source != null) {
          html.append(" — ")
              .append(escapeHtml(source.path("path").asText()))
              .append(" ")
              .append(escapeHtml(source.path("originalId").asText()));
        } else {
          html.append(" — 来源索引未找到");
        }
        html.append("</li>");
      }
      html.append("</ul>");
      if (!definition.path("unknowns").isEmpty()) {
        html.append("<p>未确认：</p><ul>");
        for (JsonNode unknown : definition.path("unknowns")) {
          html.append("<li>")
              .append(escapeHtml(unknown.path("field").asText()))
              .append("：")
              .append(escapeHtml(unknown.path("reason").asText()))
              .append("</li>");
        }
        html.append("</ul>");
      }
      html.append("</details>");
    }
  }

  private static void appendSources(StringBuilder html, Map<String, JsonNode> sources) {
    html.append("<h2>来源索引</h2>");
    sources.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry -> {
              JsonNode source = entry.getValue();
              html.append("<details><summary><code>")
                  .append(escapeHtml(entry.getKey()))
                  .append("</code></summary><p>本包短号：")
                  .append(escapeHtml(source.path("localRef").asText()))
                  .append("；原始单元：")
                  .append(escapeHtml(source.path("originalId").asText()))
                  .append("；路径：")
                  .append(escapeHtml(source.path("path").asText()))
                  .append("</p><pre>")
                  .append(escapeHtml(source.toString()))
                  .append("</pre></details>");
            });
  }

  private static void appendLimitations(StringBuilder html, JsonNode coverage, JsonNode review) {
    html.append("<h2>范围与限制</h2><p>覆盖：")
        .append(escapeHtml(coverage.path("coverageStatus").asText()))
        .append("；人工接受：")
        .append(escapeHtml(review.path("humanAcceptanceStatus").asText()))
        .append("</p><ul>");
    for (JsonNode layer : coverage.path("recognitionLayers")) {
      html.append("<li>")
          .append(escapeHtml(layer.path("layer").asText()))
          .append("：")
          .append(escapeHtml(layer.path("status").asText()))
          .append("</li>");
    }
    for (String field : List.of("readingDispositions", "taskDispositions", "clueDispositions")) {
      for (JsonNode item : coverage.path(field)) {
        String status = item.path("status").asText(item.path("outcome").asText());
        if (Set.of("READ", "PREPARED", "REVIEWED", "LINK_SUPPORTED").contains(status)) continue;
        html.append("<li>")
            .append(escapeHtml(field))
            .append("：")
            .append(escapeHtml(item.path("taskId").asText()))
            .append(" ")
            .append(escapeHtml(item.path("unitRef").asText(item.path("clueRef").asText())))
            .append(" ")
            .append(escapeHtml(status))
            .append(" ")
            .append(escapeHtml(item.path("reason").asText()))
            .append("</li>");
      }
    }
    for (JsonNode issue : review.path("assemblyIssues")) {
      html.append("<li>组装限制：")
          .append(escapeHtml(issue.path("code").asText()))
          .append(" ")
          .append(escapeHtml(issue.path("detail").asText()))
          .append("</li>");
    }
    for (JsonNode unresolved : review.path("unresolved")) {
      html.append("<li>未解：")
          .append(escapeHtml(unresolved.path("description").asText()))
          .append("</li>");
    }
    html.append("</ul>");
  }

  private static List<JsonNode> sorted(JsonNode array) {
    if (!array.isArray()) return List.of();
    List<JsonNode> rows = new ArrayList<>();
    array.forEach(rows::add);
    rows.sort(Comparator.comparing(row -> row.path("globalId").asText()));
    return List.copyOf(rows);
  }

  private static String graphLabel(String input) {
    StringBuilder safe = new StringBuilder();
    input
        .codePoints()
        .forEach(
            codePoint -> {
              if (Character.isLetterOrDigit(codePoint) || codePoint == ' ') {
                safe.appendCodePoint(codePoint);
              } else {
                // Mermaid's quoted flowchart labels decode decimal #NN; entities after parsing.
                // Encoding
                // punctuation (including the entity introducer itself) preserves the exact label
                // while
                // preventing model text from closing a quote or introducing graph instructions.
                safe.append('#').append(codePoint).append(';');
              }
            });
    return safe.toString();
  }

  private static String escapeHtml(String input) {
    return input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  private static String utf8(ImmutableBytes bytes) {
    return new String(bytes.copyToByteArray(), StandardCharsets.UTF_8);
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("ONTOLOGY_BUSINESS_OVERVIEW_INPUT_INVALID");
  }
}
