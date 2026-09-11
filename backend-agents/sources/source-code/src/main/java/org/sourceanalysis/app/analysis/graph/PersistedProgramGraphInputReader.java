package org.sourceanalysis.app.analysis.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.discovery.HttpEntryKind;
import org.sourceanalysis.app.analysis.discovery.HttpEntryPoint;
import org.sourceanalysis.app.analysis.discovery.HttpMethodCondition;
import org.sourceanalysis.app.analysis.discovery.MapperCatalogEntry;
import org.sourceanalysis.app.analysis.discovery.MapperMethodCandidate;
import org.sourceanalysis.app.analysis.discovery.MapperStatementCandidate;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;
import org.sourceanalysis.app.evidence.SourceExcerptV1;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/**
 * Internal composition boundary for fresh-reopening the published inputs consumed by Program
 * Graphs.
 *
 * <p>The reader deliberately owns no source path and does not scan a worktree. Its behavior is
 * added in small steps as the five graph builders begin consuming their exact persisted inputs.
 */
final class PersistedProgramGraphInputReader implements ProgramGraphInputReader {

  private static final String APPLICATION_PROFILE_FILE = "application-profile.json";
  private static final String CAPABILITY_REPORT_FILE = "capability-report.json";
  private static final String ENTRY_POINTS_FILE = "entry-points.jsonl";
  private static final String MAPPER_CATALOG_FILE = "mapper-catalog.jsonl";
  private static final Map<String, ExpectedArtifact> EXPECTED_ARTIFACTS =
      Map.of(
          APPLICATION_PROFILE_FILE,
          new ExpectedArtifact(
              "APPLICATION_DISCOVERY_APPLICATION_PROFILE",
              "application-discovery-application-profile-v2",
              CanonicalMediaType.APPLICATION_JSON),
          CAPABILITY_REPORT_FILE,
          new ExpectedArtifact(
              "APPLICATION_DISCOVERY_CAPABILITY_REPORT",
              "application-discovery-capability-report-v2",
              CanonicalMediaType.APPLICATION_JSON),
          ENTRY_POINTS_FILE,
          new ExpectedArtifact(
              "APPLICATION_DISCOVERY_ENTRY_POINTS",
              "application-discovery-entry-points-v2",
              CanonicalMediaType.APPLICATION_X_NDJSON),
          MAPPER_CATALOG_FILE,
          new ExpectedArtifact(
              "APPLICATION_DISCOVERY_MAPPER_CATALOG",
              "application-discovery-mapper-catalog-v2",
              CanonicalMediaType.APPLICATION_X_NDJSON));

  private final CanonicalAnalysisStepArtifactStore analysisSteps;
  private final VerifiedSourceTextReader verifiedSourceReader;
  private final CanonicalJsonCodec canonicalJson;

  PersistedProgramGraphInputReader(
      CanonicalAnalysisStepArtifactStore analysisSteps,
      VerifiedSourceTextReader verifiedSourceReader) {
    this.analysisSteps = Objects.requireNonNull(analysisSteps, "analysis step store");
    this.verifiedSourceReader =
        Objects.requireNonNull(verifiedSourceReader, "verified source text reader");
    this.canonicalJson = new CanonicalJsonCodec();
  }

  /** Reopens the exact predecessor publications and converts them to graph-builder-only input. */
  @Override
  public ReopenedProgramGraphInputs reopen(
      VerifiedSourceInventoryReference verifiedSource,
      ApplicationDiscoveryReference applicationDiscovery) {
    try {
      requireSourceReference(verifiedSource);
      requireDiscoveryReference(applicationDiscovery);
      VerifiedSourceTextSet sourceTexts = verifiedSourceReader.reopen(verifiedSource);
      CodeStructureSource source = source(sourceTexts);
      ReopenedAnalysisStepPublication publication =
          analysisSteps.reopen(applicationDiscovery.publication());
      requireDiscoveryPublication(
          publication,
          applicationDiscovery.publication(),
          verifiedSource.publication(),
          sourceTexts);
      Map<String, VerifiedCanonicalPayload> payloads = semanticPayloads(publication);
      ObjectNode profile =
          object(
              canonicalJson.parseCanonical(payloads.get(APPLICATION_PROFILE_FILE).canonicalUtf8()));
      ObjectNode capability =
          object(
              canonicalJson.parseCanonical(payloads.get(CAPABILITY_REPORT_FILE).canonicalUtf8()));
      ArtifactId applicationProfileId = ArtifactId.parse(text(profile, "applicationProfileId"));
      ArtifactReference applicationProfileRef = reference(payloads.get(APPLICATION_PROFILE_FILE));
      if (!applicationProfileRef.artifactId().value().equals(text(profile, "artifactId"))
          || !applicationProfileId.value().equals(text(capability, "applicationProfileId"))) {
        throw failure();
      }
      List<HttpEntryPoint> entries = entries(payloads.get(ENTRY_POINTS_FILE));
      List<MapperCatalogEntry> mapperCatalog = mapperCatalog(payloads.get(MAPPER_CATALOG_FILE));
      requireCoverage(capability, entries, mapperCatalog);
      return new ReopenedProgramGraphInputs(
          source,
          new ProgramGraphDiscoveryInputs(
              new CodeStructureDiscovery(
                  applicationProfileId,
                  applicationProfileRef,
                  reference(payloads.get(CAPABILITY_REPORT_FILE)),
                  reference(payloads.get(ENTRY_POINTS_FILE)),
                  reference(payloads.get(MAPPER_CATALOG_FILE)),
                  entries.stream().map(HttpEntryPoint::entryId).toList()),
              entries,
              mapperCatalog));
    } catch (ProgramGraphInputException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw failure();
    }
  }

  private static void requireSourceReference(VerifiedSourceInventoryReference reference) {
    if (reference == null
        || reference.publication().address().analysisStepKey()
            != AnalysisStepKey.VERIFIED_SOURCE_INVENTORY) {
      throw failure();
    }
  }

  private static void requireDiscoveryReference(ApplicationDiscoveryReference reference) {
    if (reference == null
        || reference.publication().address().analysisStepKey()
            != AnalysisStepKey.APPLICATION_DISCOVERY) {
      throw failure();
    }
  }

  private static CodeStructureSource source(VerifiedSourceTextSet source) {
    if (source == null) {
      throw failure();
    }
    List<CodeStructureSourceDocument> documents =
        source.documents().stream().map(PersistedProgramGraphInputReader::document).toList();
    return new CodeStructureSource(
        source.snapshotId(),
        source.inventoryScopeKind(),
        source.repositoryCompletionEligible(),
        source.sourceInventoryRef(),
        source.verifiedSnapshotRef(),
        source.controls(),
        documents);
  }

  private static CodeStructureSourceDocument document(VerifiedSourceTextDocument document) {
    return new CodeStructureSourceDocument(
        document.fileId(), document.path(), document.rawUtf8(), document.sha256());
  }

  private static void requireDiscoveryPublication(
      ReopenedAnalysisStepPublication publication,
      AnalysisStepPublicationReference expectedDiscovery,
      AnalysisStepPublicationReference expectedSource,
      VerifiedSourceTextSet source) {
    if (publication == null
        || !publication.reference().equals(expectedDiscovery)
        || !publication.reference().address().equals(publication.receipt().address())
        || publication.reference().address().analysisStepKey()
            != AnalysisStepKey.APPLICATION_DISCOVERY
        || !publication.receipt().upstreamAnalysisStepReferences().equals(List.of(expectedSource))
        || !publication.receipt().controls().equals(source.controls())
        || (publication.receipt().status() != ModuleCompletionStatus.SUCCEEDED
            && publication.receipt().status() != ModuleCompletionStatus.SUCCEEDED_WITH_GAPS)) {
      throw failure();
    }
  }

  private static Map<String, VerifiedCanonicalPayload> semanticPayloads(
      ReopenedAnalysisStepPublication publication) {
    Map<String, VerifiedCanonicalPayload> result = new HashMap<>();
    for (VerifiedCanonicalPayload payload : publication.semanticPayloads()) {
      ExpectedArtifact expected = EXPECTED_ARTIFACTS.get(payload.descriptor().fileName());
      if (expected == null
          || !expected.artifactType().equals(payload.descriptor().artifactType())
          || !expected.schemaVersion().equals(payload.descriptor().schemaVersion())
          || expected.mediaType() != payload.descriptor().mediaType()
          || result.put(payload.descriptor().fileName(), payload) != null) {
        throw failure();
      }
    }
    if (!result.keySet().equals(EXPECTED_ARTIFACTS.keySet())) {
      throw failure();
    }
    return Map.copyOf(result);
  }

  private List<HttpEntryPoint> entries(VerifiedCanonicalPayload payload) {
    List<HttpEntryPoint> entries = new ArrayList<>();
    for (ObjectNode line : jsonLines(payload.canonicalUtf8().copyToByteArray())) {
      entries.add(
          new HttpEntryPoint(
              ArtifactId.parse(text(line, "entryId")),
              enumValue(HttpEntryKind.class, text(line, "kind")),
              text(line, "protocol"),
              methodCondition(line),
              text(line, "route"),
              strings(line, "routeParts"),
              text(line, "handlerFqn"),
              strings(line, "parameterNames"),
              excerpts(line, "routeSourceExcerpts")));
    }
    return List.copyOf(entries);
  }

  private List<MapperCatalogEntry> mapperCatalog(VerifiedCanonicalPayload payload) {
    List<MapperCatalogEntry> catalog = new ArrayList<>();
    for (ObjectNode line : jsonLines(payload.canonicalUtf8().copyToByteArray())) {
      List<MapperMethodCandidate> methods = new ArrayList<>();
      for (JsonNode node : array(line, "javaMethodCandidates")) {
        ObjectNode candidate = object(node);
        methods.add(
            new MapperMethodCandidate(
                ArtifactId.parse(text(candidate, "methodCandidateId")),
                text(candidate, "signature"),
                excerpt(object(candidate.get("declarationExcerpt")))));
      }
      List<MapperStatementCandidate> statements = new ArrayList<>();
      for (JsonNode node : array(line, "xmlStatementCandidates")) {
        ObjectNode candidate = object(node);
        statements.add(
            new MapperStatementCandidate(
                ArtifactId.parse(text(candidate, "statementCandidateId")),
                text(candidate, "statementId"),
                text(candidate, "statementKind"),
                excerpt(object(candidate.get("declarationExcerpt")))));
      }
      catalog.add(
          new MapperCatalogEntry(
              ArtifactId.parse(text(line, "catalogEntryId")),
              text(line, "javaInterfaceFqn"),
              methods,
              text(line, "xmlResourcePath"),
              text(line, "xmlNamespace"),
              statements,
              text(line, "bindingState")));
    }
    return List.copyOf(catalog);
  }

  private static void requireCoverage(
      ObjectNode capability, List<HttpEntryPoint> entries, List<MapperCatalogEntry> mapperCatalog) {
    ObjectNode coverage = object(capability.get("repositoryEntryCoverage"));
    if (!ids(coverage, "entryIds")
            .equals(
                entries.stream()
                    .map(value -> value.entryId().value())
                    .collect(java.util.stream.Collectors.toSet()))
        || !ids(coverage, "mapperCatalogEntryIds")
            .equals(
                mapperCatalog.stream()
                    .map(value -> value.catalogEntryId().value())
                    .collect(java.util.stream.Collectors.toSet()))) {
      throw failure();
    }
  }

  private static Set<String> ids(ObjectNode node, String fieldName) {
    Set<String> values = new HashSet<>();
    for (JsonNode value : array(node, fieldName)) {
      if (!value.isTextual() || !values.add(value.textValue())) {
        throw failure();
      }
    }
    return Set.copyOf(values);
  }

  private List<ObjectNode> jsonLines(byte[] bytes) {
    String text = new String(bytes, StandardCharsets.UTF_8);
    if (text.isEmpty()) {
      return List.of();
    }
    if (!text.endsWith("\n")) {
      throw failure();
    }
    List<ObjectNode> lines = new ArrayList<>();
    for (String line : text.substring(0, text.length() - 1).split("\\n", -1)) {
      if (line.isBlank()) {
        throw failure();
      }
      lines.add(
          object(
              canonicalJson.parseCanonical(
                  org.sourceanalysis.app.artifact.ImmutableBytes.copyOf(
                      line.getBytes(StandardCharsets.UTF_8)))));
    }
    return List.copyOf(lines);
  }

  private static List<String> strings(ObjectNode node, String fieldName) {
    List<String> values = new ArrayList<>();
    for (JsonNode value : array(node, fieldName)) {
      if (!value.isTextual() || value.textValue().isBlank()) {
        throw failure();
      }
      values.add(value.textValue());
    }
    return List.copyOf(values);
  }

  private static HttpMethodCondition methodCondition(ObjectNode node) {
    ObjectNode condition = object(node.get("methodCondition"));
    try {
      return new HttpMethodCondition(
          HttpMethodCondition.Kind.valueOf(text(condition, "kind")), strings(condition, "methods"));
    } catch (IllegalArgumentException invalid) {
      throw failure();
    }
  }

  private static List<SourceExcerptV1> excerpts(ObjectNode node, String fieldName) {
    List<SourceExcerptV1> values = new ArrayList<>();
    for (JsonNode value : array(node, fieldName)) {
      values.add(excerpt(object(value)));
    }
    return List.copyOf(values);
  }

  private static SourceExcerptV1 excerpt(ObjectNode node) {
    ObjectNode locator = object(node.get("locator"));
    return new SourceExcerptV1(
        new SourceLocatorV1(
            ArtifactId.parse(text(locator, "fileId")),
            text(locator, "path"),
            longValue(locator, "startByte"),
            longValue(locator, "endByteExclusive"),
            intValue(locator, "startLine"),
            intValue(locator, "startColumn"),
            intValue(locator, "endLine"),
            intValue(locator, "endColumn")),
        org.sourceanalysis.app.artifact.ImmutableBytes.copyOf(
            text(node, "rawUtf8").getBytes(StandardCharsets.UTF_8)),
        Sha256Digest.parse(text(node, "rawUtf8Sha256")));
  }

  private static ArtifactReference reference(VerifiedCanonicalPayload payload) {
    return new ArtifactReference(payload.descriptor().artifactId(), payload.descriptor().sha256());
  }

  private static ObjectNode object(JsonNode node) {
    if (node instanceof ObjectNode object) {
      return object;
    }
    throw failure();
  }

  private static ArrayNode array(ObjectNode node, String fieldName) {
    if (node.get(fieldName) instanceof ArrayNode array) {
      return array;
    }
    throw failure();
  }

  private static String text(ObjectNode node, String fieldName) {
    JsonNode value = node.get(fieldName);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) {
      throw failure();
    }
    return value.textValue();
  }

  private static long longValue(ObjectNode node, String fieldName) {
    JsonNode value = node.get(fieldName);
    if (value == null || !value.isIntegralNumber()) {
      throw failure();
    }
    return value.longValue();
  }

  private static int intValue(ObjectNode node, String fieldName) {
    JsonNode value = node.get(fieldName);
    if (value == null || !value.isInt()) {
      throw failure();
    }
    return value.intValue();
  }

  private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
    try {
      return Enum.valueOf(type, value);
    } catch (IllegalArgumentException invalid) {
      throw failure();
    }
  }

  private static ProgramGraphInputException failure() {
    return new ProgramGraphInputException("PROGRAM_GRAPH_INPUT_INVALID");
  }

  private record ExpectedArtifact(
      String artifactType, String schemaVersion, CanonicalMediaType mediaType) {}
}
