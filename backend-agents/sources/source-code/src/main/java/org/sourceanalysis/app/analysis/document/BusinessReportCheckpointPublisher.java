package org.sourceanalysis.app.analysis.document;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.analysis.interpretation.material.SourceReference;
import org.sourceanalysis.app.analysis.knowledge.RepositoryBusinessKnowledge;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
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
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;

/** Installs the four reopenable outputs of a review-approved nine-section business report. */
final class BusinessReportCheckpointPublisher {

  private static final String REPORT_TYPE = "BUSINESS_DOCUMENT_REPORT";
  private static final String REPORT_SCHEMA = "business-document-report-v1";
  private static final String MARKDOWN_TYPE = "BUSINESS_DOCUMENT_MARKDOWN";
  private static final String MARKDOWN_SCHEMA = "business-document-markdown-v1";
  private static final String SOURCE_REFS_TYPE = "BUSINESS_DOCUMENT_SOURCE_REFERENCES";
  private static final String SOURCE_REFS_SCHEMA = "business-document-source-references-v1";
  private static final String VALIDATION_TYPE = "BUSINESS_DOCUMENT_VALIDATION";
  private static final String VALIDATION_SCHEMA = "business-document-validation-v1";
  private static final Comparator<String> UTF8_ORDER =
      (left, right) -> {
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        int length = Math.min(leftBytes.length, rightBytes.length);
        for (int index = 0; index < length; index++) {
          int comparison =
              Integer.compare(
                  Byte.toUnsignedInt(leftBytes[index]), Byte.toUnsignedInt(rightBytes[index]));
          if (comparison != 0) {
            return comparison;
          }
        }
        return Integer.compare(leftBytes.length, rightBytes.length);
      };

  private final CanonicalModuleArtifactStore artifacts;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  BusinessReportCheckpointPublisher(CanonicalModuleArtifactStore artifacts) {
    this.artifacts = Objects.requireNonNull(artifacts, "module artifact store");
  }

  ModulePublicationReference publish(
      RepositoryBusinessKnowledge knowledge, BusinessReportPublication publication) {
    if (knowledge.checkpoint() == null) {
      throw new IllegalArgumentException("BUSINESS_REPORT_INPUT_NOT_PERSISTED");
    }
    ReopenedModulePublication knowledgeCheckpoint = artifacts.reopen(knowledge.checkpoint());
    List<ArtifactReference> upstream =
        knowledgeCheckpoint.receipt().payloadArtifacts().stream()
            .map(value -> new ArtifactReference(value.artifactId(), value.sha256()))
            .sorted(Comparator.comparing(value -> value.artifactId().value(), UTF8_ORDER))
            .toList();
    InstalledModulePublication installed =
        artifacts.install(
            new ModuleInstallRequest(
                new AnalysisStepModuleAddress(
                    knowledge.checkpoint().address().runId(),
                    AnalysisStepKey.NINE_SECTION_DOCUMENT,
                    1,
                    "business-report-publisher"),
                "v1",
                upstream,
                knowledgeCheckpoint.receipt().controls(),
                ModuleCompletionStatus.SUCCEEDED,
                List.of(),
                List.of(
                    reportPayload(publication.businessReport()),
                    markdownPayload(publication.documentMarkdown()),
                    validationPayload(publication.validation()),
                    sourceReferencesPayload(publication.sourceReferences()))));
    return installed.reference();
  }

  private CanonicalModulePayload reportPayload(BusinessReport report) {
    ObjectNode value = reportJson(report, REPORT_SCHEMA, REPORT_TYPE);
    return standalonePayload(
        "business-report.json", "business-report", REPORT_TYPE, REPORT_SCHEMA, value);
  }

  private CanonicalModulePayload markdownPayload(String markdown) {
    byte[] bytes = markdown.getBytes(StandardCharsets.UTF_8);
    return new CanonicalModulePayload(
        "document.md",
        MARKDOWN_TYPE,
        MARKDOWN_SCHEMA,
        ArtifactId.parse(
            rawId("business-document-markdown", MARKDOWN_SCHEMA, MARKDOWN_TYPE, bytes)),
        CanonicalMediaType.TEXT_MARKDOWN,
        ImmutableBytes.copyOf(bytes));
  }

  private CanonicalModulePayload validationPayload(BusinessReportValidation validation) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("schemaVersion", VALIDATION_SCHEMA);
    value.put("artifactType", VALIDATION_TYPE);
    value.put("status", validation.status());
    value.put("sectionCount", validation.sectionCount());
    value.put("sectionOrderValid", validation.sectionOrderValid());
    value.put("sourceRefsValid", validation.sourceRefsValid());
    return standalonePayload(
        "report-validation.json", "report-validation", VALIDATION_TYPE, VALIDATION_SCHEMA, value);
  }

  private CanonicalModulePayload sourceReferencesPayload(List<SourceReference> sourceReferences) {
    StringBuilder values = new StringBuilder();
    sourceReferences.stream()
        .sorted(Comparator.comparing(SourceReference::ref, UTF8_ORDER))
        .forEach(
            source -> {
              ObjectNode value = JsonNodeFactory.instance.objectNode();
              value.put("recordType", "SOURCE_REFERENCE");
              value.put("schemaVersion", SOURCE_REFS_SCHEMA);
              value.put("ref", source.ref());
              value.put("file", source.file());
              value.put("startLine", source.startLine());
              value.put("endLine", source.endLine());
              value.put("snippet", source.snippet());
              values
                  .append(
                      new String(
                          canonicalJson.encodeCanonical(value).copyToByteArray(),
                          StandardCharsets.UTF_8))
                  .append('\n');
            });
    byte[] bytes = values.toString().getBytes(StandardCharsets.UTF_8);
    return new CanonicalModulePayload(
        "source-refs.jsonl",
        SOURCE_REFS_TYPE,
        SOURCE_REFS_SCHEMA,
        ArtifactId.parse(jsonlId("source-refs", SOURCE_REFS_SCHEMA, SOURCE_REFS_TYPE, bytes)),
        CanonicalMediaType.APPLICATION_X_NDJSON,
        ImmutableBytes.copyOf(bytes));
  }

  private ObjectNode reportJson(BusinessReport report, String schema, String type) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("schemaVersion", schema);
    value.put("artifactType", type);
    value.put("title", report.title());
    ArrayNode sections = value.putArray("sections");
    report
        .sections()
        .forEach(
            section -> {
              ObjectNode sectionJson = sections.addObject();
              sectionJson.put("number", section.number());
              sectionJson.put("title", section.title());
              contents(sectionJson.putArray("paragraphs"), section.paragraphs());
              contents(sectionJson.putArray("items"), section.items());
            });
    return value;
  }

  private static void contents(ArrayNode target, List<BusinessReportContent> contents) {
    contents.forEach(
        content -> {
          ObjectNode value = target.addObject();
          value.put("text", content.text());
          ArrayNode refs = value.putArray("refs");
          content.refs().forEach(refs::add);
        });
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
