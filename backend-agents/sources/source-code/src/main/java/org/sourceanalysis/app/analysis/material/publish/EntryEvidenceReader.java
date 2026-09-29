package org.sourceanalysis.app.analysis.material.publish;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepPublisherModuleProvenance;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/**
 * Path-free reader for the new Step05 directory.
 *
 * <p>The reader selects only filenames derived from a validated complete entry ID, then returns the
 * installed canonical document bytes. It never accepts a filename or a filesystem path from a
 * caller. Rich record hydration is deliberately kept at the consumer boundary: this reader first
 * proves the saved module/index closure rather than reopening a checkout or re-running assembly.
 */
public final class EntryEvidenceReader {

  private static final Comparator<String> UTF8_ORDER = EntryEvidenceReader::compareUtf8;
  private final CanonicalModuleArtifactStore modules;
  private final CanonicalAnalysisStepArtifactStore steps;
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  public EntryEvidenceReader(
      CanonicalModuleArtifactStore modules, CanonicalAnalysisStepArtifactStore steps) {
    this.modules = Objects.requireNonNull(modules, "entry-evidence module store");
    this.steps = Objects.requireNonNull(steps, "entry-evidence analysis-step store");
  }

  /** Reads exactly one installed entry document using its complete canonical entry ID. */
  public EntryDocument read(AnalysisStepPublicationReference reference, String entryId) {
    Directory directory = reopen(reference);
    if (entryId == null || !entryId.matches("entry:[0-9a-f]{64}")) {
      throw invalid();
    }
    return directory.entries().stream()
        .filter(document -> entryId.equals(document.entryId()))
        .findFirst()
        .orElseThrow(EntryEvidenceReader::invalid);
  }

  /** Reopens and validates every installed directory member without touching a caller path. */
  public Directory reopen(AnalysisStepPublicationReference reference) {
    try {
      Objects.requireNonNull(reference, "entry-evidence publication");
      ReopenedAnalysisStepPublication step = steps.reopen(reference);
      if (!step.reference().equals(reference)
          || reference.address().analysisStepKey() != AnalysisStepKey.BUSINESS_FLOWS
          || !(step.receipt().publicationProvenance()
              instanceof AnalysisStepPublisherModuleProvenance provenance)) {
        throw invalid();
      }
      ReopenedModulePublication module =
          modules.reopen(provenance.publisherSpecificationModuleReference());
      if (!module.reference().equals(provenance.publisherSpecificationModuleReference())
          || !(module.reference().address()
              instanceof org.sourceanalysis.app.artifact.AnalysisStepModuleAddress address)
          || address.analysisStepKey() != AnalysisStepKey.BUSINESS_FLOWS
          || address.moduleNumber() != 4
          || !EntryEvidencePublisher.MODULE_KEY.equals(address.moduleKey())
          || !EntryEvidencePublisher.MODULE_VERSION.equals(module.receipt().moduleVersion())
          || !module.receipt().controls().equals(step.receipt().controls())) {
        throw invalid();
      }
      List<VerifiedCanonicalPayload> payloads = step.semanticPayloads();
      if (payloads.size() < 2 || !samePayloads(payloads, module.payloads())) {
        throw invalid();
      }
      VerifiedCanonicalPayload index = only(payloads, EntryEvidencePublisher.INDEX_TYPE);
      VerifiedCanonicalPayload coverage = only(payloads, EntryEvidencePublisher.COVERAGE_TYPE);
      requireDescriptor(
          index,
          EntryEvidencePublisher.INDEX_FILE,
          EntryEvidencePublisher.INDEX_TYPE,
          EntryEvidencePublisher.INDEX_SCHEMA,
          CanonicalMediaType.APPLICATION_JSON);
      requireDescriptor(
          coverage,
          EntryEvidencePublisher.COVERAGE_FILE,
          EntryEvidencePublisher.COVERAGE_TYPE,
          EntryEvidencePublisher.COVERAGE_SCHEMA,
          CanonicalMediaType.APPLICATION_X_NDJSON);
      ObjectNode indexDocument = object(json.parseCanonical(index.canonicalUtf8()));
      requireText(indexDocument, "schemaVersion", EntryEvidencePublisher.INDEX_SCHEMA);
      requireText(indexDocument, "producer", EntryEvidencePublisher.PRODUCER);
      ObjectNode header = object(indexDocument.get("header"));
      boolean selectedBasisWire = header.has("sourceBasis");
      if (selectedBasisWire && !validSelectedSourceBasis(header)) {
        throw invalid();
      }
      ArrayNode entries = array(indexDocument.get("entries"));
      List<EntryDocument> documents = new ArrayList<>();
      Set<String> entryIds = new HashSet<>();
      String previous = null;
      for (JsonNode item : entries) {
        ObjectNode indexed = object(item);
        String entryId = text(indexed, "entryId");
        String file = text(indexed, "file");
        if (!entryId.matches("entry:[0-9a-f]{64}")
            || !EntryEvidencePublisher.entryFileName(entryId).equals(file)
            || !entryIds.add(entryId)
            || (previous != null && UTF8_ORDER.compare(previous, entryId) >= 0)
            || !isValidIndexEntry(indexed)) {
          throw invalid();
        }
        VerifiedCanonicalPayload entry =
            payloads.stream()
                .filter(payload -> file.equals(payload.descriptor().fileName()))
                .findFirst()
                .orElseThrow(EntryEvidenceReader::invalid);
        requireDescriptor(
            entry,
            file,
            EntryEvidencePublisher.ENTRY_TYPE,
            EntryEvidencePublisher.ENTRY_SCHEMA,
            CanonicalMediaType.APPLICATION_JSON);
        ObjectNode document = object(json.parseCanonical(entry.canonicalUtf8()));
        if (!entryId.equals(text(document, "entryId"))
            || !EntryEvidencePublisher.ENTRY_SCHEMA.equals(text(document, "schemaVersion"))
            || !EntryEvidencePublisher.PRODUCER.equals(text(document, "producer"))
            || !header.equals(object(document.get("header")))
            || !sameEntryLineage(document, header, selectedBasisWire)
            || !validHttpEntry(document, entryId)) {
          throw invalid();
        }
        requireSourceReferences(document);
        documents.add(new EntryDocument(entryId, entry.canonicalUtf8()));
        previous = entryId;
      }
      long actualEntryPayloads =
          payloads.stream()
              .filter(
                  payload ->
                      EntryEvidencePublisher.ENTRY_TYPE.equals(payload.descriptor().artifactType()))
              .count();
      if (actualEntryPayloads != documents.size()) {
        throw invalid();
      }
      requireCoverage(coverage.canonicalUtf8(), header, entryIds);
      return new Directory(index.canonicalUtf8(), coverage.canonicalUtf8(), documents);
    } catch (RuntimeException failure) {
      if (failure instanceof IllegalArgumentException
          && "ENTRY_EVIDENCE_READER_INVALID".equals(failure.getMessage())) {
        throw failure;
      }
      throw invalid(failure);
    }
  }

  /** Installed entry JSON selected by complete identity, not by a caller-supplied path. */
  public record EntryDocument(String entryId, ImmutableBytes canonicalJson) {
    public EntryDocument {
      if (entryId == null || !entryId.matches("entry:[0-9a-f]{64}")) {
        throw invalid();
      }
      canonicalJson = Objects.requireNonNull(canonicalJson, "entry-evidence canonical JSON");
    }
  }

  /** The verified directory entry point plus its frontend coverage and exact entry documents. */
  public record Directory(
      ImmutableBytes indexCanonicalJson,
      ImmutableBytes frontendCoverageCanonicalJsonl,
      List<EntryDocument> entries) {
    public Directory {
      indexCanonicalJson = Objects.requireNonNull(indexCanonicalJson, "entry-evidence index JSON");
      frontendCoverageCanonicalJsonl =
          Objects.requireNonNull(frontendCoverageCanonicalJsonl, "entry-evidence coverage JSONL");
      entries = List.copyOf(Objects.requireNonNull(entries, "entry-evidence documents"));
      String previous = null;
      for (EntryDocument entry : entries) {
        if (previous != null && UTF8_ORDER.compare(previous, entry.entryId()) >= 0) {
          throw invalid();
        }
        previous = entry.entryId();
      }
    }
  }

  private static VerifiedCanonicalPayload only(
      List<VerifiedCanonicalPayload> payloads, String artifactType) {
    List<VerifiedCanonicalPayload> matches =
        payloads.stream()
            .filter(payload -> artifactType.equals(payload.descriptor().artifactType()))
            .toList();
    if (matches.size() != 1) {
      throw invalid();
    }
    return matches.get(0);
  }

  private static void requireDescriptor(
      VerifiedCanonicalPayload payload,
      String fileName,
      String artifactType,
      String schemaVersion,
      CanonicalMediaType mediaType) {
    if (!fileName.equals(payload.descriptor().fileName())
        || !artifactType.equals(payload.descriptor().artifactType())
        || !schemaVersion.equals(payload.descriptor().schemaVersion())
        || mediaType != payload.descriptor().mediaType()) {
      throw invalid();
    }
  }

  private static boolean samePayloads(
      List<VerifiedCanonicalPayload> stepPayloads, List<VerifiedCanonicalPayload> modulePayloads) {
    if (stepPayloads.size() != modulePayloads.size()) return false;
    for (int index = 0; index < stepPayloads.size(); index++) {
      VerifiedCanonicalPayload step = stepPayloads.get(index);
      VerifiedCanonicalPayload module = modulePayloads.get(index);
      if (!step.descriptor().equals(module.descriptor())
          || !step.canonicalUtf8().equals(module.canonicalUtf8())) {
        return false;
      }
    }
    return true;
  }

  private static boolean isValidIndexEntry(ObjectNode entry) {
    JsonNode route = entry.get("route");
    JsonNode handler = entry.get("handlerFqn");
    JsonNode methodKey = entry.get("methodKey");
    return route != null
        && route.isTextual()
        && route.textValue().startsWith("/")
        && handler != null
        && handler.isTextual()
        && !handler.textValue().isBlank()
        && methodKey != null
        && methodKey.isTextual()
        && !methodKey.textValue().isBlank()
        && entry.get("methodCondition") instanceof ObjectNode;
  }

  private void requireCoverage(
      ImmutableBytes coverageBytes, ObjectNode expectedHeader, Set<String> entryIds) {
    String content = strictUtf8(coverageBytes);
    String[] lines = content.split("\\n", -1);
    if (lines.length < 2 || !lines[lines.length - 1].isEmpty()) {
      throw invalid();
    }
    Set<String> requestIds = new HashSet<>();
    for (int index = 0; index < lines.length - 1; index++) {
      if (lines[index].isEmpty()) throw invalid();
      ObjectNode line =
          object(
              json.parseCanonical(
                  ImmutableBytes.copyOf(lines[index].getBytes(StandardCharsets.UTF_8))));
      requireText(line, "schemaVersion", EntryEvidencePublisher.COVERAGE_SCHEMA);
      String type = text(line, "recordType");
      if (index == 0) {
        if (!"HEADER".equals(type)
            || !EntryEvidencePublisher.PRODUCER.equals(text(line, "producer"))
            || !expectedHeader.equals(object(line.get("header")))) {
          throw invalid();
        }
        continue;
      }
      if (!"REQUEST_COVERAGE".equals(type)) throw invalid();
      ObjectNode payload = object(line.get("payload"));
      String requestId = text(payload, "requestId");
      if (!requestIds.add(requestId)) throw invalid();
      String resolution = text(payload, "resolution");
      Set<String> candidates = requireCoverageEntryIds(array(payload.get("entryIds")), entryIds);
      Set<String> included =
          requireCoverageEntryIds(array(payload.get("includedEntryIds")), entryIds);
      if (!candidates.containsAll(included)) throw invalid();
      boolean uniquelyIncluded = "MATCHED_UNIQUE".equals(resolution) && included.size() == 1;
      if (uniquelyIncluded) {
        if (payload.has("request")
            || payload.has("units")
            || !EntryEvidencePublisher.entryFileName(included.iterator().next())
                .equals(text(payload, "entryFile"))
            || candidates.size() != 1
            || !candidates.equals(included)) {
          throw invalid();
        }
      } else {
        ObjectNode request = object(payload.get("request"));
        if (!requestId.equals(text(request, "requestId"))
            || !(payload.get("units") instanceof ArrayNode)) {
          throw invalid();
        }
      }
    }
  }

  private static Set<String> requireCoverageEntryIds(ArrayNode values, Set<String> entryIds) {
    Set<String> seen = new HashSet<>();
    for (JsonNode value : values) {
      if (!value.isTextual()
          || !entryIds.contains(value.textValue())
          || !seen.add(value.textValue())) {
        throw invalid();
      }
    }
    return Set.copyOf(seen);
  }

  private static void requireSourceReferences(ObjectNode document) {
    ArrayNode references = array(document.get("sourceRefs"));
    Set<String> identities = new HashSet<>();
    for (JsonNode value : references) {
      ObjectNode reference = object(value);
      String identity = text(reference, "reference");
      String path = text(reference, "path");
      if (!identities.add(identity)
          || path.startsWith("/")
          || path.contains("..")
          || !reference.get("kind").isTextual()) {
        throw invalid();
      }
      JsonNode hash = reference.get("sourceSha256");
      if (hash != null
          && !hash.isNull()
          && (!hash.isTextual() || !hash.textValue().matches("[0-9a-f]{64}"))) {
        throw invalid();
      }
    }
  }

  /** Verifies the per-entry self-contained lineage duplicates the directory header exactly. */
  private static boolean sameEntryLineage(
      ObjectNode document, ObjectNode header, boolean selectedBasisWire) {
    JsonNode sourceBasis = document.get("sourceBasis");
    JsonNode headerSource = header.get(selectedBasisWire ? "sourceBasis" : "sourceInventory");
    JsonNode upstream = document.get("upstream");
    return sourceBasis != null
        && sourceBasis.equals(headerSource)
        && upstream instanceof ObjectNode values
        && same(values, "applicationDiscovery", header, "applicationDiscovery")
        && same(values, "navigationPublication", header, "navigationPublication")
        && same(values, "persistencePublication", header, "persistencePublication")
        && same(values, "frontendPublication", header, "frontendPublication");
  }

  /**
   * New v3 entry evidence writes a complete prepared-source basis in its directory header. The
   * reader validates its source snapshot and publication against the adjacent source-inventory
   * reference. Older debug directories omit this field and remain on the explicit legacy branch in
   * {@link #sameEntryLineage(ObjectNode, ObjectNode, boolean)}.
   */
  private static boolean validSelectedSourceBasis(ObjectNode header) {
    try {
      ObjectNode basis = object(header.get("sourceBasis"));
      if (!"PREPARED_V1".equals(text(basis, "kind"))
          || !text(basis, "snapshotId").equals(text(header, "sourceSnapshotId"))
          || !text(basis, "effectiveScopeDigest").matches("[0-9a-f]{64}")
          || !(basis.get("legacyCapture") == null || basis.get("legacyCapture").isNull())) {
        return false;
      }
      ObjectNode prepared = object(basis.get("preparedSource"));
      if (!text(prepared, "sourceVersionId").equals(text(basis, "snapshotId"))
          || !validArtifactReference(object(prepared.get("schemaBundleRef")))
          || !validArtifactReference(object(prepared.get("artifactPolicyRegistryRef")))) {
        return false;
      }
      return samePublication(
          object(object(header.get("sourceInventory")).get("publication")),
          object(prepared.get("publication")));
    } catch (RuntimeException malformed) {
      return false;
    }
  }

  private static boolean samePublication(ObjectNode sourceInventory, ObjectNode selectedBasis) {
    ObjectNode sourceAddress = object(sourceInventory.get("address"));
    ObjectNode basisAddress = object(selectedBasis.get("address"));
    String sourceStep = scalar(sourceAddress.get("analysisStepKey"));
    String basisStep = scalar(basisAddress.get("analysisStepKey"));
    return sameScalar(sourceAddress.get("runId"), basisAddress.get("runId"))
        && (sourceStep.equals(basisStep)
            || ("VERIFIED_SOURCE_INVENTORY".equals(sourceStep)
                && AnalysisStepKey.VERIFIED_SOURCE_INVENTORY.wireValue().equals(basisStep)))
        && sameScalar(
            sourceInventory.get("analysisStepArtifactRoot"),
            selectedBasis.get("analysisStepArtifactRoot"))
        && sameScalar(
            sourceInventory.get("analysisStepReceiptId"),
            selectedBasis.get("analysisStepReceiptId"))
        && sameScalar(
            sourceInventory.get("analysisStepReceiptSha256"),
            selectedBasis.get("analysisStepReceiptSha256"));
  }

  private static boolean validArtifactReference(ObjectNode reference) {
    return text(reference, "artifactId").matches("[a-z][a-z0-9-]{0,47}:[0-9a-f]{64}")
        && text(reference, "sha256").matches("[0-9a-f]{64}");
  }

  private static boolean sameScalar(JsonNode first, JsonNode second) {
    return scalar(first).equals(scalar(second));
  }

  private static String scalar(JsonNode value) {
    if (value != null && value.isTextual() && !value.textValue().isBlank()) {
      return value.textValue();
    }
    if (value instanceof ObjectNode object && object.size() == 1) {
      return text(object, "value");
    }
    throw invalid();
  }

  private static boolean same(
      ObjectNode first, String firstField, ObjectNode second, String secondField) {
    JsonNode firstValue = first.get(firstField);
    JsonNode secondValue = second.get(secondField);
    return firstValue != null && secondValue != null && firstValue.equals(secondValue);
  }

  /** Validates the route source snippets that make an entry document self-contained. */
  private static boolean validHttpEntry(ObjectNode document, String entryId) {
    ObjectNode entry = object(document.get("entry"));
    if (!entryId.equals(text(entry, "entryId"))
        || !entry.get("kind").isTextual()
        || !"HTTP".equals(text(entry, "protocol"))
        || !entry.get("route").isTextual()
        || !entry.get("route").textValue().startsWith("/")
        || !entry.get("handlerFqn").isTextual()
        || !entry.get("methodKey").isTextual()
        || !validMethodCondition(entry.get("methodCondition"))
        || !validMethodRange(entry.get("methodRange"))) {
      return false;
    }
    ArrayNode excerpts = array(entry.get("routeSourceExcerpts"));
    if (excerpts.size() != 2) return false;
    for (JsonNode value : excerpts) {
      ObjectNode excerpt = object(value);
      ObjectNode locator = object(excerpt.get("locator"));
      String path = text(locator, "path");
      if (path.startsWith("/")
          || path.contains("..")
          || !text(locator, "fileId").matches("[a-z][a-z0-9-]{0,47}:[0-9a-f]{64}")
          || !nonnegative(locator, "startByte")
          || !nonnegative(locator, "endByteExclusive")
          || locator.get("endByteExclusive").longValue() < locator.get("startByte").longValue()
          || !positive(locator, "startLine")
          || !nonnegative(locator, "startColumn")
          || !positive(locator, "endLine")
          || !nonnegative(locator, "endColumn")
          || !excerpt.get("rawUtf8").isTextual()
          || !text(excerpt, "rawUtf8Sha256").matches("[0-9a-f]{64}")
          || !text(excerpt, "rawUtf8Sha256")
              .equals(
                  sha256(excerpt.get("rawUtf8").textValue().getBytes(StandardCharsets.UTF_8)))) {
        return false;
      }
    }
    return true;
  }

  private static boolean validMethodCondition(JsonNode value) {
    if (!(value instanceof ObjectNode condition)) return false;
    String kind = text(condition, "kind");
    ArrayNode methods = array(condition.get("methods"));
    if ("UNRESTRICTED".equals(kind)) return methods.isEmpty();
    if (!"EXPLICIT".equals(kind) || methods.isEmpty()) return false;
    List<String> canonical =
        List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE");
    int last = -1;
    for (JsonNode method : methods) {
      if (!method.isTextual()) return false;
      int current = canonical.indexOf(method.textValue());
      if (current < 0 || current <= last) return false;
      last = current;
    }
    return true;
  }

  private static boolean validMethodRange(JsonNode value) {
    if (!(value instanceof ObjectNode range)) return false;
    if (!nonnegative(range, "startOffsetUtf16")
        || !nonnegative(range, "lengthUtf16")
        || !positive(range, "startLine")
        || !positive(range, "endLine")) {
      return false;
    }
    return range.get("endLine").longValue() >= range.get("startLine").longValue();
  }

  private static boolean nonnegative(ObjectNode value, String field) {
    JsonNode number = value.get(field);
    return number != null && number.canConvertToLong() && number.longValue() >= 0;
  }

  private static boolean positive(ObjectNode value, String field) {
    JsonNode number = value.get(field);
    return number != null && number.canConvertToLong() && number.longValue() > 0;
  }

  private static String sha256(byte[] bytes) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 unavailable", unavailable);
    }
  }

  private static ObjectNode object(JsonNode value) {
    if (!(value instanceof ObjectNode object)) throw invalid();
    return object;
  }

  private static ArrayNode array(JsonNode value) {
    if (!(value instanceof ArrayNode array)) throw invalid();
    return array;
  }

  private static String text(ObjectNode value, String field) {
    JsonNode text = value.get(field);
    if (text == null || !text.isTextual() || text.textValue().isBlank()) throw invalid();
    return text.textValue();
  }

  private static void requireText(ObjectNode value, String field, String expected) {
    if (!expected.equals(text(value, field))) throw invalid();
  }

  private static String strictUtf8(ImmutableBytes bytes) {
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
          .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
          .decode(java.nio.ByteBuffer.wrap(bytes.copyToByteArray()))
          .toString();
    } catch (java.nio.charset.CharacterCodingException invalidUtf8) {
      throw invalid(invalidUtf8);
    }
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("ENTRY_EVIDENCE_READER_INVALID");
  }

  private static IllegalArgumentException invalid(Throwable cause) {
    return new IllegalArgumentException("ENTRY_EVIDENCE_READER_INVALID", cause);
  }

  private static int compareUtf8(String first, String second) {
    return java.util.Arrays.compareUnsigned(
        first.getBytes(StandardCharsets.UTF_8), second.getBytes(StandardCharsets.UTF_8));
  }
}
