package org.sourceanalysis.app.analysis.knowledge;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.sourceanalysis.app.analysis.interpretation.material.SourceReference;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;

/** Canonical, receipt-last publisher for the four Step07 process-discovery outputs. */
public final class CanonicalBusinessProcessPublisher implements BusinessProcessPublisher {

  static final String CATALOG_TYPE = "REPOSITORY_KNOWLEDGE_BUSINESS_PROCESS_CATALOG";
  static final String CATALOG_SCHEMA = "repository-business-process-catalog-v1";
  static final String COVERAGE_TYPE = "REPOSITORY_KNOWLEDGE_PROCESS_COVERAGE";
  static final String COVERAGE_SCHEMA = "repository-business-process-coverage-v1";
  static final String MARKDOWN_TYPE = "REPOSITORY_KNOWLEDGE_BUSINESS_PROCESSES_MARKDOWN";
  static final String MARKDOWN_SCHEMA = "repository-business-process-markdown-v1";
  static final String SOURCE_REFS_TYPE = "REPOSITORY_KNOWLEDGE_SOURCE_REFERENCES";
  static final String SOURCE_REFS_SCHEMA = "repository-business-process-source-references-v1";

  private final CanonicalModuleArtifactStore inputArtifacts;
  private final CanonicalModuleArtifactStore outputArtifacts;
  private final ArtifactControls outputControls;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  public CanonicalBusinessProcessPublisher(CanonicalModuleArtifactStore artifacts) {
    this.inputArtifacts = Objects.requireNonNull(artifacts, "module artifact store");
    this.outputArtifacts = artifacts;
    this.outputControls = null;
  }

  public CanonicalBusinessProcessPublisher(
      CanonicalModuleArtifactStore inputArtifacts,
      CanonicalModuleArtifactStore outputArtifacts,
      ArtifactControls outputControls) {
    this.inputArtifacts = Objects.requireNonNull(inputArtifacts, "input module artifact store");
    this.outputArtifacts = Objects.requireNonNull(outputArtifacts, "output module artifact store");
    this.outputControls = Objects.requireNonNull(outputControls, "output artifact controls");
  }

  @Override
  public BusinessProcessPublication publish(ProcessDiscoveryResult result) {
    Objects.requireNonNull(result, "process discovery result");
    String markdown = BusinessProcessMarkdownRenderer.render(result.catalog(), result.coverage());
    List<SourceReference> sources = referencedSources(result);
    ReopenedModulePublication activities = inputArtifacts.reopen(result.activityCheckpoint());
    ReopenedModulePublication materials = inputArtifacts.reopen(result.materialCheckpoint());
    if (!activities.receipt().controls().equals(materials.receipt().controls())) {
      throw new IllegalArgumentException("BUSINESS_PROCESS_UPSTREAM_CONTROLS_MISMATCH");
    }
    Map<String, ArtifactReference> upstream = new LinkedHashMap<>();
    List.of(activities, materials).stream()
        .flatMap(value -> value.receipt().payloadArtifacts().stream())
        .map(value -> new ArtifactReference(value.artifactId(), value.sha256()))
        .sorted(Comparator.comparing(value -> value.artifactId().value()))
        .forEach(value -> upstream.putIfAbsent(value.artifactId().value(), value));
    ModuleCompletionStatus status =
        "COMPLETE".equals(result.coverage().semanticDeliveryStatus())
            ? ModuleCompletionStatus.SUCCEEDED
            : ModuleCompletionStatus.SUCCEEDED_WITH_GAPS;
    List<String> gaps =
        status == ModuleCompletionStatus.SUCCEEDED
            ? List.of()
            : List.of("PROCESS_SEMANTIC_DELIVERY_PARTIAL");
    InstalledModulePublication installed =
        outputArtifacts.install(
            new ModuleInstallRequest(
                new AnalysisStepModuleAddress(
                    result.outputRunId(),
                    AnalysisStepKey.REPOSITORY_KNOWLEDGE,
                    1,
                    "business-process-publisher"),
                "v1",
                List.copyOf(upstream.values()),
                outputControls == null ? activities.receipt().controls() : outputControls,
                status,
                gaps,
                List.of(
                    markdownPayload(markdown),
                    coveragePayload(result.coverage()),
                    catalogPayload(result.catalog()),
                    sourceReferencesPayload(sources))));
    return new BusinessProcessPublication(
        result.catalog(), result.coverage(), markdown, sources, installed.reference());
  }

  private List<SourceReference> referencedSources(ProcessDiscoveryResult result) {
    Map<String, SourceReference> all = new LinkedHashMap<>();
    result.sourceReferences().stream()
        .sorted(Comparator.comparing(SourceReference::ref))
        .forEach(source -> all.put(source.ref(), source));
    List<String> required = new ArrayList<>();
    result.catalog().processes().forEach(process -> collectProcessRefs(required, process));
    result.catalog().processRelations().forEach(value -> required.addAll(value.sourceRefs()));
    result
        .catalog()
        .directActivityKnowledgeItems()
        .forEach(value -> required.addAll(value.sourceRefs()));
    return required.stream()
        .distinct()
        .sorted()
        .map(
            ref -> {
              SourceReference source = all.get(ref);
              if (source == null) {
                throw new IllegalArgumentException("BUSINESS_PROCESS_SOURCE_REF_UNKNOWN");
              }
              return source;
            })
        .toList();
  }

  private static void collectProcessRefs(
      List<String> target, RepositoryBusinessProcessCatalog.BusinessProcess process) {
    target.addAll(process.sourceRefs());
    process.activityUses().forEach(value -> target.addAll(value.sourceRefs()));
    process.stages().forEach(value -> target.addAll(value.sourceRefs()));
    process.businessRules().forEach(value -> target.addAll(value.sourceRefs()));
    process.knowledgeItems().forEach(value -> target.addAll(value.sourceRefs()));
  }

  private CanonicalModulePayload catalogPayload(RepositoryBusinessProcessCatalog catalog) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("schemaVersion", CATALOG_SCHEMA);
    value.put("artifactType", CATALOG_TYPE);
    ArrayNode areas = value.putArray("businessAreas");
    catalog.businessAreas().stream()
        .sorted(Comparator.comparing(RepositoryBusinessProcessCatalog.BusinessArea::areaId))
        .forEach(area -> areaJson(areas.addObject(), area));
    ArrayNode aliases = value.putArray("aliases");
    catalog.aliases().stream()
        .sorted(Comparator.comparing(RepositoryBusinessProcessCatalog.BusinessAlias::canonicalName))
        .forEach(
            alias -> {
              ObjectNode target = aliases.addObject();
              target.put("canonicalName", alias.canonicalName());
              strings(target.putArray("aliases"), alias.aliases());
            });
    ArrayNode processes = value.putArray("processes");
    catalog.processes().stream()
        .sorted(Comparator.comparing(RepositoryBusinessProcessCatalog.BusinessProcess::processId))
        .forEach(process -> processJson(processes.addObject(), process));
    ArrayNode relations = value.putArray("processRelations");
    catalog.processRelations().stream()
        .sorted(
            Comparator.comparing(RepositoryBusinessProcessCatalog.ProcessRelation::fromProcessId)
                .thenComparing(RepositoryBusinessProcessCatalog.ProcessRelation::toProcessId)
                .thenComparing(RepositoryBusinessProcessCatalog.ProcessRelation::relationType))
        .forEach(relation -> relationJson(relations.addObject(), relation));
    strings(value.putArray("standaloneActivityIds"), catalog.standaloneActivityIds());
    strings(value.putArray("unclassifiedActivityIds"), catalog.unclassifiedActivityIds());
    ArrayNode knowledge = value.putArray("directActivityKnowledgeItems");
    catalog
        .directActivityKnowledgeItems()
        .forEach(item -> knowledgeJson(knowledge.addObject(), item));
    strings(value.putArray("pendingConfirmations"), catalog.pendingConfirmations());
    return standalonePayload(
        "repository-business-process-catalog.json",
        "repository-business-process-catalog",
        CATALOG_TYPE,
        CATALOG_SCHEMA,
        value);
  }

  private CanonicalModulePayload coveragePayload(ProcessCoverage coverage) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("schemaVersion", COVERAGE_SCHEMA);
    value.put("artifactType", COVERAGE_TYPE);
    ArrayNode activities = value.putArray("activityDispositions");
    coverage.activityDispositions().stream()
        .sorted(Comparator.comparing(ProcessCoverage.ActivityDisposition::activityId))
        .forEach(
            item -> {
              ObjectNode target = activities.addObject();
              target.put("activityId", item.activityId());
              target.put("disposition", item.disposition());
              target.put("reason", item.reason());
            });
    ArrayNode candidates = value.putArray("candidateDispositions");
    coverage.candidateDispositions().stream()
        .sorted(Comparator.comparing(ProcessCoverage.CandidateDisposition::candidateId))
        .forEach(
            item -> {
              ObjectNode target = candidates.addObject();
              target.put("candidateId", item.candidateId());
              target.put("disposition", item.disposition());
              target.put("reason", item.reason());
            });
    ArrayNode processes = value.putArray("reviewedProcessDispositions");
    coverage.reviewedProcessDispositions().stream()
        .sorted(Comparator.comparing(ProcessCoverage.ReviewedProcessDisposition::processId))
        .forEach(
            item -> {
              ObjectNode target = processes.addObject();
              target.put("processId", item.processId());
              target.put("disposition", item.disposition());
              if (item.targetProcessId() == null) {
                target.putNull("targetProcessId");
              } else {
                target.put("targetProcessId", item.targetProcessId());
              }
              target.put("reason", item.reason());
            });
    value.put("coverageStatus", coverage.coverageStatus());
    value.put("semanticDeliveryStatus", coverage.semanticDeliveryStatus());
    return standalonePayload(
        "process-coverage.json", "process-coverage", COVERAGE_TYPE, COVERAGE_SCHEMA, value);
  }

  private CanonicalModulePayload markdownPayload(String markdown) {
    byte[] bytes = markdown.getBytes(StandardCharsets.UTF_8);
    return new CanonicalModulePayload(
        "business-processes.md",
        MARKDOWN_TYPE,
        MARKDOWN_SCHEMA,
        ArtifactId.parse(
            rawId("business-processes-markdown", MARKDOWN_SCHEMA, MARKDOWN_TYPE, bytes)),
        CanonicalMediaType.TEXT_MARKDOWN,
        ImmutableBytes.copyOf(bytes));
  }

  private CanonicalModulePayload sourceReferencesPayload(List<SourceReference> sources) {
    StringBuilder jsonl = new StringBuilder();
    sources.forEach(
        source -> {
          ObjectNode value = JsonNodeFactory.instance.objectNode();
          value.put("recordType", "SOURCE_REFERENCE");
          value.put("schemaVersion", SOURCE_REFS_SCHEMA);
          value.put("ref", source.ref());
          value.put("file", source.file());
          value.put("startLine", source.startLine());
          value.put("endLine", source.endLine());
          value.put("snippet", source.snippet());
          jsonl
              .append(
                  new String(
                      canonicalJson.encodeCanonical(value).copyToByteArray(),
                      StandardCharsets.UTF_8))
              .append('\n');
        });
    byte[] bytes = jsonl.toString().getBytes(StandardCharsets.UTF_8);
    return new CanonicalModulePayload(
        "source-refs.jsonl",
        SOURCE_REFS_TYPE,
        SOURCE_REFS_SCHEMA,
        ArtifactId.parse(
            jsonlId("business-process-source-refs", SOURCE_REFS_SCHEMA, SOURCE_REFS_TYPE, bytes)),
        CanonicalMediaType.APPLICATION_X_NDJSON,
        ImmutableBytes.copyOf(bytes));
  }

  private static void areaJson(
      ObjectNode target, RepositoryBusinessProcessCatalog.BusinessArea area) {
    target.put("areaId", area.areaId());
    target.put("name", area.name());
    target.put("purpose", area.purpose());
    strings(target.putArray("processIds"), area.processIds());
  }

  private static void processJson(
      ObjectNode target, RepositoryBusinessProcessCatalog.BusinessProcess process) {
    target.put("processId", process.processId());
    target.put("name", process.name());
    target.put("purpose", process.purpose());
    target.put("scope", process.scope());
    strings(target.putArray("participants"), process.participants());
    strings(target.putArray("businessObjects"), process.businessObjects());
    ArrayNode uses = target.putArray("activityUses");
    process
        .activityUses()
        .forEach(
            use -> {
              ObjectNode value = uses.addObject();
              value.put("activityUseId", use.activityUseId());
              value.put("activityId", use.activityId());
              value.put("role", use.role());
              value.put("variant", use.variant());
              strings(value.putArray("statementRefs"), use.statementRefs());
              strings(value.putArray("sourceRefs"), use.sourceRefs());
            });
    ArrayNode stages = target.putArray("stages");
    process.stages().forEach(stage -> stageJson(stages.addObject(), stage));
    strings(target.putArray("branches"), process.branches());
    ArrayNode rules = target.putArray("businessRules");
    process.businessRules().forEach(rule -> ruleJson(rules.addObject(), rule));
    strings(target.putArray("endResults"), process.endResults());
    strings(target.putArray("supportActivityUseIds"), process.supportActivityUseIds());
    ArrayNode knowledge = target.putArray("knowledgeItems");
    process.knowledgeItems().forEach(item -> knowledgeJson(knowledge.addObject(), item));
    strings(target.putArray("pendingConnections"), process.pendingConnections());
    strings(target.putArray("sourceRefs"), process.sourceRefs());
  }

  private static void stageJson(
      ObjectNode target, RepositoryBusinessProcessCatalog.ProcessStage stage) {
    target.put("order", stage.order());
    target.put("name", stage.name());
    strings(target.putArray("activityUseIds"), stage.activityUseIds());
    strings(target.putArray("entryConditions"), stage.entryConditions());
    strings(target.putArray("actions"), stage.actions());
    strings(target.putArray("stateChanges"), stage.stateChanges());
    strings(target.putArray("rejectionConditions"), stage.rejectionConditions());
    strings(target.putArray("outcomes"), stage.outcomes());
    strings(target.putArray("transitions"), stage.transitions());
    target.put("certainty", stage.certainty());
    strings(target.putArray("statementRefs"), stage.statementRefs());
    strings(target.putArray("sourceRefs"), stage.sourceRefs());
  }

  private static void ruleJson(
      ObjectNode target, RepositoryBusinessProcessCatalog.BusinessRule rule) {
    target.put("subject", rule.subject());
    target.put("when", rule.when());
    target.put("actionOrDecision", rule.actionOrDecision());
    if (rule.otherwise() == null) {
      target.putNull("otherwise");
    } else {
      target.put("otherwise", rule.otherwise());
    }
    target.put("result", rule.result());
    target.put("certainty", rule.certainty());
    strings(target.putArray("statementRefs"), rule.statementRefs());
    strings(target.putArray("sourceRefs"), rule.sourceRefs());
  }

  private static void knowledgeJson(
      ObjectNode target, RepositoryBusinessProcessCatalog.KnowledgeItem item) {
    target.put("kind", item.kind());
    target.put("text", item.text());
    target.put("ownerId", item.ownerId());
    target.put("certainty", item.certainty());
    strings(target.putArray("statementRefs"), item.statementRefs());
    strings(target.putArray("sourceRefs"), item.sourceRefs());
  }

  private static void relationJson(
      ObjectNode target, RepositoryBusinessProcessCatalog.ProcessRelation relation) {
    target.put("fromProcessId", relation.fromProcessId());
    target.put("toProcessId", relation.toProcessId());
    target.put("relationType", relation.relationType());
    target.put("description", relation.description());
    target.put("certainty", relation.certainty());
    strings(target.putArray("sourceRefs"), relation.sourceRefs());
  }

  private CanonicalModulePayload standalonePayload(
      String fileName, String prefix, String type, String schema, ObjectNode value) {
    String id = standaloneId(prefix, schema, type, value);
    value.put("artifactId", id);
    return new CanonicalModulePayload(
        fileName,
        type,
        schema,
        ArtifactId.parse(id),
        CanonicalMediaType.APPLICATION_JSON,
        canonicalJson.encodeCanonical(value));
  }

  private String standaloneId(String prefix, String schema, String type, ObjectNode value) {
    ObjectNode withoutId = value.deepCopy();
    withoutId.remove("artifactId");
    return prefix
        + ":"
        + sha256(
            frame("canonical-standalone-json-artifact-id-v1"),
            frame(schema),
            frame(type),
            frame(canonicalJson.encodeCanonical(withoutId)));
  }

  private static String rawId(String prefix, String schema, String type, byte[] bytes) {
    return prefix
        + ":"
        + sha256(frame("canonical-raw-artifact-id-v1"), frame(schema), frame(type), frame(bytes));
  }

  private static String jsonlId(String prefix, String schema, String type, byte[] bytes) {
    return prefix
        + ":"
        + sha256(frame("canonical-jsonl-artifact-id-v1"), frame(schema), frame(type), frame(bytes));
  }

  private static void strings(ArrayNode target, List<String> values) {
    values.forEach(target::add);
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(ImmutableBytes value) {
    return frame(value.copyToByteArray());
  }

  private static byte[] frame(byte[] value) {
    return ByteBuffer.allocate(Long.BYTES + value.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(value.length)
        .put(value)
        .array();
  }

  private static String sha256(byte[]... values) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (byte[] value : values) {
        digest.update(value);
      }
      return java.util.HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }
}
