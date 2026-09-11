package org.sourceanalysis.app.analysis.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.interpretation.material.SourceReference;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/** Fresh-reopens the four immutable outputs of a review-approved business report. */
final class BusinessReportCheckpointReader {

  private static final String REPORT_FILE = "business-report.json";
  private static final String REPORT_TYPE = "BUSINESS_DOCUMENT_REPORT";
  private static final String REPORT_SCHEMA = "business-document-report-v1";
  private static final String MARKDOWN_FILE = "document.md";
  private static final String MARKDOWN_TYPE = "BUSINESS_DOCUMENT_MARKDOWN";
  private static final String MARKDOWN_SCHEMA = "business-document-markdown-v1";
  private static final String SOURCE_REFS_FILE = "source-refs.jsonl";
  private static final String SOURCE_REFS_TYPE = "BUSINESS_DOCUMENT_SOURCE_REFERENCES";
  private static final String SOURCE_REFS_SCHEMA = "business-document-source-references-v1";
  private static final String VALIDATION_FILE = "report-validation.json";
  private static final String VALIDATION_TYPE = "BUSINESS_DOCUMENT_VALIDATION";
  private static final String VALIDATION_SCHEMA = "business-document-validation-v1";
  private static final Set<String> REPORT_FIELDS =
      Set.of("artifactId", "artifactType", "schemaVersion", "sections", "title");
  private static final Set<String> SECTION_FIELDS =
      Set.of("items", "number", "paragraphs", "title");
  private static final Set<String> CONTENT_FIELDS = Set.of("refs", "text");
  private static final Set<String> SOURCE_REF_FIELDS =
      Set.of("endLine", "file", "recordType", "ref", "schemaVersion", "snippet", "startLine");
  private static final Set<String> VALIDATION_FIELDS =
      Set.of(
          "artifactId",
          "artifactType",
          "schemaVersion",
          "sectionCount",
          "sectionOrderValid",
          "sourceRefsValid",
          "status");

  private final CanonicalModuleArtifactStore artifacts;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  BusinessReportCheckpointReader(CanonicalModuleArtifactStore artifacts) {
    this.artifacts = Objects.requireNonNull(artifacts, "module artifact store");
  }

  /** Reopens one complete report publication without source work, rendering, or Provider calls. */
  BusinessReportPublication reopen(ModulePublicationReference checkpoint) {
    try {
      ReopenedModulePublication reopened = artifacts.reopen(checkpoint);
      verifyCheckpoint(checkpoint, reopened);
      Map<String, VerifiedCanonicalPayload> payloads = payloadsByName(reopened.payloads());
      List<SourceReference> sourceReferences = sourceReferences(payloads.get(SOURCE_REFS_FILE));
      Set<String> allowedReferences =
          sourceReferences.stream()
              .map(SourceReference::ref)
              .collect(java.util.stream.Collectors.toSet());
      BusinessReport report = report(payloads.get(REPORT_FILE), allowedReferences);
      BusinessReportValidation validation = validation(payloads.get(VALIDATION_FILE), report);
      String markdown = markdown(payloads.get(MARKDOWN_FILE));
      return new BusinessReportPublication(
          report, markdown, sourceReferences, validation, checkpoint);
    } catch (BusinessReportCheckpointException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw failure("BUSINESS_REPORT_CHECKPOINT_INVALID", failure);
    }
  }

  /** Rebuilds the document from approved report data, never from saved Markdown or a Provider. */
  String rerender(BusinessReportPublication publication) {
    Objects.requireNonNull(publication, "business report publication");
    return BusinessReportMarkdownRenderer.render(
        publication.businessReport(), publication.sourceReferences());
  }

  private static void verifyCheckpoint(
      ModulePublicationReference checkpoint, ReopenedModulePublication reopened) {
    if (!checkpoint.equals(reopened.reference())
        || !(checkpoint.address() instanceof AnalysisStepModuleAddress address)
        || address.analysisStepKey() != AnalysisStepKey.NINE_SECTION_DOCUMENT
        || address.moduleNumber() != 1
        || !"business-report-publisher".equals(address.moduleKey())
        || reopened.receipt().status() != ModuleCompletionStatus.SUCCEEDED
        || reopened.payloads().size() != 4) {
      throw failure("BUSINESS_REPORT_CHECKPOINT_INVALID", null);
    }
  }

  private Map<String, VerifiedCanonicalPayload> payloadsByName(
      List<VerifiedCanonicalPayload> payloads) {
    Map<String, VerifiedCanonicalPayload> byName = new HashMap<>();
    for (VerifiedCanonicalPayload payload : payloads) {
      if (byName.put(payload.descriptor().fileName(), payload) != null) {
        throw failure("BUSINESS_REPORT_CHECKPOINT_INVALID", null);
      }
    }
    if (!byName
        .keySet()
        .equals(Set.of(REPORT_FILE, MARKDOWN_FILE, SOURCE_REFS_FILE, VALIDATION_FILE))) {
      throw failure("BUSINESS_REPORT_CHECKPOINT_INVALID", null);
    }
    requireDescriptor(
        byName.get(REPORT_FILE), REPORT_TYPE, REPORT_SCHEMA, CanonicalMediaType.APPLICATION_JSON);
    requireDescriptor(
        byName.get(MARKDOWN_FILE),
        MARKDOWN_TYPE,
        MARKDOWN_SCHEMA,
        CanonicalMediaType.TEXT_MARKDOWN);
    requireDescriptor(
        byName.get(SOURCE_REFS_FILE),
        SOURCE_REFS_TYPE,
        SOURCE_REFS_SCHEMA,
        CanonicalMediaType.APPLICATION_X_NDJSON);
    requireDescriptor(
        byName.get(VALIDATION_FILE),
        VALIDATION_TYPE,
        VALIDATION_SCHEMA,
        CanonicalMediaType.APPLICATION_JSON);
    return Map.copyOf(byName);
  }

  private static void requireDescriptor(
      VerifiedCanonicalPayload payload, String type, String schema, CanonicalMediaType mediaType) {
    if (!type.equals(payload.descriptor().artifactType())
        || !schema.equals(payload.descriptor().schemaVersion())
        || mediaType != payload.descriptor().mediaType()) {
      throw failure("BUSINESS_REPORT_CHECKPOINT_INVALID", null);
    }
  }

  private BusinessReport report(VerifiedCanonicalPayload payload, Set<String> allowedReferences) {
    ObjectNode value = parseObject(payload.canonicalUtf8(), REPORT_FIELDS);
    requireStandaloneIdentity(value, payload, REPORT_TYPE, REPORT_SCHEMA);
    String title = requiredText(value, "title");
    JsonNode sections = value.path("sections");
    if (!sections.isArray() || sections.size() != BusinessReportPublisher.sectionTitles().size()) {
      throw failure("BUSINESS_REPORT_CHECKPOINT_INVALID", null);
    }
    List<BusinessReportSection> restored = new ArrayList<>();
    for (int index = 0; index < sections.size(); index++) {
      JsonNode section = sections.get(index);
      if (!(section instanceof ObjectNode object)
          || !fields(object).equals(SECTION_FIELDS)
          || !object.path("number").canConvertToInt()
          || object.path("number").intValue() != index + 1
          || !BusinessReportPublisher.sectionTitles()
              .get(index)
              .equals(requiredText(object, "title"))) {
        throw failure("BUSINESS_REPORT_CHECKPOINT_INVALID", null);
      }
      restored.add(
          new BusinessReportSection(
              index + 1,
              BusinessReportPublisher.sectionTitles().get(index),
              contents(object.path("paragraphs"), allowedReferences),
              contents(object.path("items"), allowedReferences)));
    }
    return new BusinessReport(title, restored);
  }

  private List<BusinessReportContent> contents(JsonNode values, Set<String> allowedReferences) {
    if (!values.isArray()) {
      throw failure("BUSINESS_REPORT_CHECKPOINT_INVALID", null);
    }
    List<BusinessReportContent> restored = new ArrayList<>();
    for (JsonNode value : values) {
      if (!(value instanceof ObjectNode content) || !fields(content).equals(CONTENT_FIELDS)) {
        throw failure("BUSINESS_REPORT_CHECKPOINT_INVALID", null);
      }
      JsonNode references = content.path("refs");
      if (!references.isArray()) {
        throw failure("BUSINESS_REPORT_CHECKPOINT_INVALID", null);
      }
      List<String> refs = new ArrayList<>();
      Set<String> seen = new HashSet<>();
      for (JsonNode reference : references) {
        if (!reference.isTextual()
            || !allowedReferences.contains(reference.textValue())
            || !seen.add(reference.textValue())) {
          throw failure("BUSINESS_REPORT_CHECKPOINT_INVALID", null);
        }
        refs.add(reference.textValue());
      }
      restored.add(new BusinessReportContent(requiredText(content, "text"), refs));
    }
    return List.copyOf(restored);
  }

  private List<SourceReference> sourceReferences(VerifiedCanonicalPayload payload) {
    byte[] bytes = payload.canonicalUtf8().copyToByteArray();
    if (bytes.length == 0) {
      return List.of();
    }
    String jsonl = strictUtf8(bytes);
    if (!jsonl.endsWith("\n") || jsonl.indexOf('\r') >= 0) {
      throw failure("BUSINESS_REPORT_CHECKPOINT_INVALID", null);
    }
    String[] lines = jsonl.substring(0, jsonl.length() - 1).split("\n", -1);
    List<SourceReference> restored = new ArrayList<>();
    String previous = null;
    for (String line : lines) {
      if (line.isEmpty()) {
        throw failure("BUSINESS_REPORT_CHECKPOINT_INVALID", null);
      }
      ObjectNode value =
          parseObject(
              ImmutableBytes.copyOf(line.getBytes(StandardCharsets.UTF_8)), SOURCE_REF_FIELDS);
      if (!"SOURCE_REFERENCE".equals(requiredText(value, "recordType"))
          || !SOURCE_REFS_SCHEMA.equals(requiredText(value, "schemaVersion"))
          || !value.path("startLine").canConvertToInt()
          || !value.path("endLine").canConvertToInt()) {
        throw failure("BUSINESS_REPORT_CHECKPOINT_INVALID", null);
      }
      SourceReference reference =
          new SourceReference(
              requiredText(value, "ref"),
              requiredText(value, "file"),
              value.path("startLine").intValue(),
              value.path("endLine").intValue(),
              requiredText(value, "snippet"));
      if (previous != null && compareUtf8(previous, reference.ref()) >= 0) {
        throw failure("BUSINESS_REPORT_CHECKPOINT_INVALID", null);
      }
      previous = reference.ref();
      restored.add(reference);
    }
    return List.copyOf(restored);
  }

  private BusinessReportValidation validation(
      VerifiedCanonicalPayload payload, BusinessReport report) {
    ObjectNode value = parseObject(payload.canonicalUtf8(), VALIDATION_FIELDS);
    requireStandaloneIdentity(value, payload, VALIDATION_TYPE, VALIDATION_SCHEMA);
    if (!value.path("sectionCount").canConvertToInt()
        || value.path("sectionCount").intValue() != report.sections().size()
        || !value.path("sectionOrderValid").isBoolean()
        || !value.path("sectionOrderValid").booleanValue()
        || !value.path("sourceRefsValid").isBoolean()
        || !value.path("sourceRefsValid").booleanValue()
        || !"VALID".equals(requiredText(value, "status"))) {
      throw failure("BUSINESS_REPORT_CHECKPOINT_INVALID", null);
    }
    return new BusinessReportValidation(
        value.path("status").textValue(),
        value.path("sectionCount").intValue(),
        value.path("sectionOrderValid").booleanValue(),
        value.path("sourceRefsValid").booleanValue());
  }

  private String markdown(VerifiedCanonicalPayload payload) {
    return strictUtf8(payload.canonicalUtf8().copyToByteArray());
  }

  private ObjectNode parseObject(ImmutableBytes bytes, Set<String> expectedFields) {
    JsonNode parsed = canonicalJson.parseCanonical(bytes);
    if (!(parsed instanceof ObjectNode value) || !fields(value).equals(expectedFields)) {
      throw failure("BUSINESS_REPORT_CHECKPOINT_INVALID", null);
    }
    return value;
  }

  private static Set<String> fields(ObjectNode value) {
    Set<String> result = new HashSet<>();
    value.fieldNames().forEachRemaining(result::add);
    return result;
  }

  private static void requireStandaloneIdentity(
      ObjectNode value, VerifiedCanonicalPayload payload, String type, String schema) {
    if (!schema.equals(requiredText(value, "schemaVersion"))
        || !type.equals(requiredText(value, "artifactType"))
        || !payload.descriptor().artifactId().value().equals(requiredText(value, "artifactId"))) {
      throw failure("BUSINESS_REPORT_CHECKPOINT_INVALID", null);
    }
  }

  private static String requiredText(ObjectNode value, String field) {
    JsonNode node = value.path(field);
    if (!node.isTextual() || node.textValue().isBlank()) {
      throw failure("BUSINESS_REPORT_CHECKPOINT_INVALID", null);
    }
    return node.textValue();
  }

  private static String strictUtf8(byte[] bytes) {
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes))
          .toString();
    } catch (CharacterCodingException invalid) {
      throw failure("BUSINESS_REPORT_CHECKPOINT_INVALID", invalid);
    }
  }

  private static int compareUtf8(String first, String second) {
    byte[] firstBytes = first.getBytes(StandardCharsets.UTF_8);
    byte[] secondBytes = second.getBytes(StandardCharsets.UTF_8);
    int length = Math.min(firstBytes.length, secondBytes.length);
    for (int index = 0; index < length; index++) {
      int comparison =
          Integer.compare(
              Byte.toUnsignedInt(firstBytes[index]), Byte.toUnsignedInt(secondBytes[index]));
      if (comparison != 0) {
        return comparison;
      }
    }
    return Integer.compare(firstBytes.length, secondBytes.length);
  }

  private static BusinessReportCheckpointException failure(String code, Throwable cause) {
    return new BusinessReportCheckpointException(code, cause);
  }

  private static final class BusinessReportCheckpointException extends IllegalArgumentException {

    private BusinessReportCheckpointException(String code, Throwable cause) {
      super(code, cause);
    }
  }
}
