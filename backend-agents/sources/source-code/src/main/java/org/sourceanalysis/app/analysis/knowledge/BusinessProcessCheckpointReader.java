package org.sourceanalysis.app.analysis.knowledge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.HashMap;
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

/** Fresh-reopens one complete Step07 business-process publication without model or source work. */
public final class BusinessProcessCheckpointReader {

  private static final String MARKDOWN_FILE = "business-processes.md";
  private static final String COVERAGE_FILE = "process-coverage.json";
  private static final String CATALOG_FILE = "repository-business-process-catalog.json";
  private static final String SOURCE_REFS_FILE = "source-refs.jsonl";
  private static final String SOURCES_MARKDOWN_FILE = "sources.md";

  private final CanonicalModuleArtifactStore artifacts;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
  private final ObjectMapper objectMapper = new ObjectMapper();

  public BusinessProcessCheckpointReader(CanonicalModuleArtifactStore artifacts) {
    this.artifacts = Objects.requireNonNull(artifacts, "module artifact store");
  }

  public BusinessProcessPublication reopen(ModulePublicationReference checkpoint) {
    try {
      ReopenedModulePublication reopened = artifacts.reopen(checkpoint);
      verifyCheckpoint(checkpoint, reopened);
      Map<String, VerifiedCanonicalPayload> payloads = payloadsByName(reopened.payloads());
      RepositoryBusinessProcessCatalog catalog = catalog(payloads.get(CATALOG_FILE));
      ProcessCoverage coverage = coverage(payloads.get(COVERAGE_FILE));
      List<SourceReference> sourceReferences = sourceReferences(payloads.get(SOURCE_REFS_FILE));
      requireSourceReferenceClosure(catalog, sourceReferences);
      String markdown = strictUtf8(payloads.get(MARKDOWN_FILE).canonicalUtf8());
      String sourcesMarkdown = strictUtf8(payloads.get(SOURCES_MARKDOWN_FILE).canonicalUtf8());
      String rendered = BusinessProcessMarkdownRenderer.render(catalog, coverage, sourceReferences);
      if (!rendered.equals(markdown)) {
        throw failure("BUSINESS_PROCESS_MARKDOWN_NONDETERMINISTIC", null);
      }
      String renderedSources = SourcesMarkdownRenderer.render(sourceReferences);
      if (!renderedSources.equals(sourcesMarkdown)) {
        throw failure("BUSINESS_PROCESS_SOURCES_MARKDOWN_NONDETERMINISTIC", null);
      }
      return new BusinessProcessPublication(
          catalog, coverage, markdown, sourceReferences, sourcesMarkdown, checkpoint);
    } catch (BusinessProcessCheckpointException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw failure("BUSINESS_PROCESS_CHECKPOINT_INVALID", failure);
    }
  }

  public String rerender(BusinessProcessPublication publication) {
    Objects.requireNonNull(publication, "business process publication");
    return BusinessProcessMarkdownRenderer.render(
        publication.catalog(), publication.coverage(), publication.sourceReferences());
  }

  /** Re-renders the portable sources view without rereading source files or calling a Provider. */
  public String rerenderSources(BusinessProcessPublication publication) {
    Objects.requireNonNull(publication, "business process publication");
    return SourcesMarkdownRenderer.render(publication.sourceReferences());
  }

  private static void verifyCheckpoint(
      ModulePublicationReference checkpoint, ReopenedModulePublication reopened) {
    if (!checkpoint.equals(reopened.reference())
        || !(checkpoint.address() instanceof AnalysisStepModuleAddress address)
        || address.analysisStepKey() != AnalysisStepKey.REPOSITORY_KNOWLEDGE
        || address.moduleNumber() != 1
        || !"business-process-publisher".equals(address.moduleKey())
        || !"v2".equals(reopened.receipt().moduleVersion())
        || (reopened.receipt().status() != ModuleCompletionStatus.SUCCEEDED
            && reopened.receipt().status() != ModuleCompletionStatus.SUCCEEDED_WITH_GAPS)
        || reopened.payloads().size() != 5) {
      throw failure("BUSINESS_PROCESS_CHECKPOINT_INVALID", null);
    }
  }

  private static Map<String, VerifiedCanonicalPayload> payloadsByName(
      List<VerifiedCanonicalPayload> payloads) {
    Map<String, VerifiedCanonicalPayload> byName = new HashMap<>();
    payloads.forEach(
        payload -> {
          if (byName.put(payload.descriptor().fileName(), payload) != null) {
            throw failure("BUSINESS_PROCESS_CHECKPOINT_INVALID", null);
          }
        });
    if (!byName
        .keySet()
        .equals(
            Set.of(
                MARKDOWN_FILE,
                COVERAGE_FILE,
                CATALOG_FILE,
                SOURCE_REFS_FILE,
                SOURCES_MARKDOWN_FILE))) {
      throw failure("BUSINESS_PROCESS_CHECKPOINT_INVALID", null);
    }
    descriptor(
        byName.get(MARKDOWN_FILE),
        CanonicalBusinessProcessPublisher.MARKDOWN_TYPE,
        CanonicalBusinessProcessPublisher.MARKDOWN_SCHEMA,
        CanonicalMediaType.TEXT_MARKDOWN);
    descriptor(
        byName.get(COVERAGE_FILE),
        CanonicalBusinessProcessPublisher.COVERAGE_TYPE,
        CanonicalBusinessProcessPublisher.COVERAGE_SCHEMA,
        CanonicalMediaType.APPLICATION_JSON);
    descriptor(
        byName.get(CATALOG_FILE),
        CanonicalBusinessProcessPublisher.CATALOG_TYPE,
        CanonicalBusinessProcessPublisher.CATALOG_SCHEMA,
        CanonicalMediaType.APPLICATION_JSON);
    descriptor(
        byName.get(SOURCE_REFS_FILE),
        CanonicalBusinessProcessPublisher.SOURCE_REFS_TYPE,
        CanonicalBusinessProcessPublisher.SOURCE_REFS_SCHEMA,
        CanonicalMediaType.APPLICATION_X_NDJSON);
    descriptor(
        byName.get(SOURCES_MARKDOWN_FILE),
        CanonicalBusinessProcessPublisher.SOURCES_MARKDOWN_TYPE,
        CanonicalBusinessProcessPublisher.SOURCES_MARKDOWN_SCHEMA,
        CanonicalMediaType.TEXT_MARKDOWN);
    return Map.copyOf(byName);
  }

  private RepositoryBusinessProcessCatalog catalog(VerifiedCanonicalPayload payload) {
    ObjectNode value = parseObject(payload.canonicalUtf8());
    metadata(
        value,
        CanonicalBusinessProcessPublisher.CATALOG_TYPE,
        CanonicalBusinessProcessPublisher.CATALOG_SCHEMA);
    ObjectNode body = value.deepCopy();
    body.remove(List.of("artifactId", "artifactType", "schemaVersion"));
    return convert(body, RepositoryBusinessProcessCatalog.class);
  }

  private ProcessCoverage coverage(VerifiedCanonicalPayload payload) {
    ObjectNode value = parseObject(payload.canonicalUtf8());
    metadata(
        value,
        CanonicalBusinessProcessPublisher.COVERAGE_TYPE,
        CanonicalBusinessProcessPublisher.COVERAGE_SCHEMA);
    ObjectNode body = value.deepCopy();
    body.remove(List.of("artifactId", "artifactType", "schemaVersion"));
    return convert(body, ProcessCoverage.class);
  }

  private List<SourceReference> sourceReferences(VerifiedCanonicalPayload payload) {
    String jsonl = strictUtf8(payload.canonicalUtf8());
    if (jsonl.isEmpty()) {
      return List.of();
    }
    if (!jsonl.endsWith("\n") || jsonl.indexOf('\r') >= 0) {
      throw failure("BUSINESS_PROCESS_CHECKPOINT_INVALID", null);
    }
    return jsonl
        .lines()
        .map(
            line -> {
              ObjectNode value =
                  parseObject(ImmutableBytes.copyOf(line.getBytes(StandardCharsets.UTF_8)));
              if (!"SOURCE_REFERENCE".equals(value.path("recordType").asText())
                  || !CanonicalBusinessProcessPublisher.SOURCE_REFS_SCHEMA.equals(
                      value.path("schemaVersion").asText())) {
                throw failure("BUSINESS_PROCESS_CHECKPOINT_INVALID", null);
              }
              return new SourceReference(
                  text(value, "ref"),
                  text(value, "file"),
                  integer(value, "startLine"),
                  integer(value, "endLine"),
                  text(value, "snippet"));
        })
        .toList();
  }

  private static void requireSourceReferenceClosure(
      RepositoryBusinessProcessCatalog catalog, List<SourceReference> sourceReferences) {
    Set<String> actual = new HashSet<>();
    for (SourceReference source : sourceReferences) {
      try {
        SourcesMarkdownRenderer.requireSafeReference(source.ref());
      } catch (IllegalArgumentException invalid) {
        throw failure("BUSINESS_PROCESS_SOURCE_REFERENCE_CLOSURE_INVALID", invalid);
      }
      if (!actual.add(source.ref())) {
        throw failure("BUSINESS_PROCESS_SOURCE_REFERENCE_CLOSURE_INVALID", null);
      }
    }
    Set<String> required =
        new HashSet<>(CanonicalBusinessProcessPublisher.referencedSourceRefs(catalog));
    if (!actual.equals(required)) {
      throw failure("BUSINESS_PROCESS_SOURCE_REFERENCE_CLOSURE_INVALID", null);
    }
  }

  private static void descriptor(
      VerifiedCanonicalPayload payload,
      String artifactType,
      String schema,
      CanonicalMediaType mediaType) {
    if (!artifactType.equals(payload.descriptor().artifactType())
        || !schema.equals(payload.descriptor().schemaVersion())
        || mediaType != payload.descriptor().mediaType()) {
      throw failure("BUSINESS_PROCESS_CHECKPOINT_INVALID", null);
    }
  }

  private static void metadata(ObjectNode value, String artifactType, String schema) {
    if (!artifactType.equals(value.path("artifactType").asText())
        || !schema.equals(value.path("schemaVersion").asText())
        || !value.path("artifactId").isTextual()) {
      throw failure("BUSINESS_PROCESS_CHECKPOINT_INVALID", null);
    }
  }

  private ObjectNode parseObject(ImmutableBytes bytes) {
    JsonNode value = canonicalJson.parseCanonical(bytes);
    if (!(value instanceof ObjectNode object)) {
      throw failure("BUSINESS_PROCESS_CHECKPOINT_INVALID", null);
    }
    return object;
  }

  private <T> T convert(ObjectNode value, Class<T> type) {
    try {
      return objectMapper.treeToValue(value, type);
    } catch (JsonProcessingException failure) {
      throw failure("BUSINESS_PROCESS_CHECKPOINT_INVALID", failure);
    }
  }

  private static String text(ObjectNode value, String field) {
    if (!value.path(field).isTextual() || value.path(field).asText().isBlank()) {
      throw failure("BUSINESS_PROCESS_CHECKPOINT_INVALID", null);
    }
    return value.path(field).asText();
  }

  private static int integer(ObjectNode value, String field) {
    if (!value.path(field).canConvertToInt()) {
      throw failure("BUSINESS_PROCESS_CHECKPOINT_INVALID", null);
    }
    return value.path(field).intValue();
  }

  private static String strictUtf8(ImmutableBytes bytes) {
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes.copyToByteArray()))
          .toString();
    } catch (CharacterCodingException failure) {
      throw failure("BUSINESS_PROCESS_CHECKPOINT_INVALID", failure);
    }
  }

  private static BusinessProcessCheckpointException failure(String code, Throwable cause) {
    return new BusinessProcessCheckpointException(code, cause);
  }
}
