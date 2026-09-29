package org.sourceanalysis.app.analysis.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepPublisherModuleProvenance;
import org.sourceanalysis.app.artifact.ArtifactDescriptor;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;
import org.sourceanalysis.app.capture.preparation.PreparedSourceArchive;

/** Strict reader for the v3 prepared-source publication; historical v2 readers remain separate. */
public final class SourcePreparationReader {

  private final CanonicalModuleArtifactStore modules;
  private final CanonicalAnalysisStepArtifactStore steps;
  private final PreparedSourceArchive archive;
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  public SourcePreparationReader(
      CanonicalModuleArtifactStore modules,
      CanonicalAnalysisStepArtifactStore steps,
      PreparedSourceArchive archive) {
    this.modules = Objects.requireNonNull(modules, "canonical module artifact store");
    this.steps = Objects.requireNonNull(steps, "canonical analysis-step artifact store");
    this.archive = Objects.requireNonNull(archive, "prepared source archive");
  }

  /**
   * Reopens a full report even if readiness remains NEEDS_DECISION; source consumption is gated
   * later.
   */
  public SavedSourcePreparation reopen(AnalysisStepPublicationReference reportReference) {
    String check = "report reference";
    try {
      if (reportReference == null
          || reportReference.address().analysisStepKey()
              != AnalysisStepKey.VERIFIED_SOURCE_INVENTORY) {
        throw invalid();
      }
      check = "analysis-step receipt";
      ReopenedAnalysisStepPublication report = steps.reopen(reportReference);
      if (!report.reference().equals(reportReference)
          || !(report.receipt().publicationProvenance()
              instanceof AnalysisStepPublisherModuleProvenance provenance)) {
        throw invalid();
      }
      check = "publisher module receipt";
      ReopenedModulePublication publisher =
          modules.reopen(provenance.publisherSpecificationModuleReference());
      if (!"v3".equals(publisher.receipt().moduleVersion())
          || !publisher.reference().equals(provenance.publisherSpecificationModuleReference())
          || publisher.receipt().status() != report.receipt().status()
          || !publisher.receipt().gapRefs().equals(report.receipt().gapRefs())) {
        throw invalid();
      }
      check = "four payload descriptors";
      Map<String, VerifiedCanonicalPayload> payloads = exactPayloads(report.semanticPayloads());
      verifyPayload(
          payloads.get("source-input.json"),
          SourcePreparationPublisher.INPUT_TYPE,
          SourcePreparationPublisher.INPUT_SCHEMA);
      verifyPayload(
          payloads.get("source-inventory.jsonl"),
          SourcePreparationPublisher.INVENTORY_TYPE,
          SourcePreparationPublisher.INVENTORY_SCHEMA);
      verifyPayload(
          payloads.get("source-issues.jsonl"),
          SourcePreparationPublisher.ISSUES_TYPE,
          SourcePreparationPublisher.ISSUES_SCHEMA);
      verifyPayload(
          payloads.get("source-preparation-result.json"),
          SourcePreparationPublisher.RESULT_TYPE,
          SourcePreparationPublisher.RESULT_SCHEMA);
      ObjectNode input = object(payloads.get("source-input.json"));
      ObjectNode result = object(payloads.get("source-preparation-result.json"));
      String sourceVersionId = text(input, "sourceVersionId");
      check = "private archive";
      PreparedSourceArchive.StoredCapture stored =
          archive.reopen(org.sourceanalysis.app.artifact.ArtifactId.parse(sourceVersionId));
      check = "saved controls and receipt gaps";
      boolean safelyRegistered =
          stored.assessment().readiness() != SourcePreparationReadiness.BLOCKED;
      if ((safelyRegistered && !sourceVersionId.equals(text(result, "sourceVersionId")))
          || (!safelyRegistered
              && (result.get("sourceVersionId") == null
                  || !result.get("sourceVersionId").isNull()))) {
        throw invalid();
      }
      if (!stored.controls().equals(report.receipt().controls())
          || !stored.assessment().readiness().name().equals(text(result, "readiness"))
          || !stored.request().operation().name().equals(text(result, "operation"))
          || (safelyRegistered
              ? !stored.sourceRegistrationRef().equals(reference(input, "sourceRegistrationRef"))
              : !isNull(input, "sourceRegistrationRef"))
          || (safelyRegistered
              ? !stored.captureReceiptRef().equals(reference(input, "captureReceiptRef"))
              : !isNull(input, "captureReceiptRef"))
          || (safelyRegistered
              ? !stored.snapshotManifestRef().equals(reference(input, "snapshotManifestRef"))
              : !isNull(input, "snapshotManifestRef"))
          || !stored.preparationProfileRef().equals(reference(input, "preparationProfileRef"))
          || !stored.preparationToolchainRef().equals(reference(input, "preparationToolchainRef"))
          || !stored.schemaBundleRef().equals(reference(input, "schemaBundleRef"))
          || !stored.resourceBudgetRef().equals(reference(input, "resourceBudgetRef"))
          || !stored.capabilityProfileRef().equals(reference(input, "capabilityProfileRef"))
          || !stored.sourceVersionId().value().equals(sourceVersionId)
          || !report.receipt().gapRefs().equals(SourcePreparationPublisher.gapRefs(stored.result()))
          || report.receipt().status()
              != (report.receipt().gapRefs().isEmpty()
                  ? ModuleCompletionStatus.SUCCEEDED
                  : ModuleCompletionStatus.SUCCEEDED_WITH_GAPS)) {
        throw invalid();
      }
      check = "private M1/M2 publication links";
      PreparedSourceArchive.PublicationLinks links =
          archive.reopenPublicationLinks(stored.sourceVersionId(), reportReference.address());
      check = "M1 receipt";
      ReopenedModulePublication m1 = modules.reopen(links.requestAdmission());
      check = "M2 receipt";
      ReopenedModulePublication m2 = modules.reopen(links.sourceIndex());
      check = "M1/M2/M3 publication closure";
      verifyPrerequisiteModules(
          reportReference, report, publisher, links, m1, m2, stored, safelyRegistered);
      check = "saved standalone payload projections";
      verifyStandaloneProjection(
          input,
          SourcePreparationPublisher.INPUT_TYPE,
          SourcePreparationPublisher.INPUT_SCHEMA,
          SourcePreparationPublisher.inputBody(stored));
      verifyStandaloneProjection(
          result,
          SourcePreparationPublisher.RESULT_TYPE,
          SourcePreparationPublisher.RESULT_SCHEMA,
          SourcePreparationPublisher.resultBody(stored));
      check = "saved JSONL payload projections";
      requireJsonlProjection(
          "inventory",
          jsonlRows(payloads.get("source-inventory.jsonl")),
          SourcePreparationPublisher.inventoryRows(stored.result()));
      requireJsonlProjection(
          "issues",
          jsonlRows(payloads.get("source-issues.jsonl")),
          SourcePreparationPublisher.issueRows(stored.result()));
      check = "step and publisher payload equivalence";
      if (!report.semanticPayloads().equals(publisher.payloads())) {
        throw invalid();
      }
      PreparedSourceReference sourceVersion =
          safelyRegistered
              ? new PreparedSourceReference(
                  stored.sourceVersionId(),
                  reportReference,
                  stored.schemaBundleRef(),
                  report.receipt().controls().artifactPolicyRegistryRef())
              : null;
      PreparedSourcePublicationFacts publicationFacts =
          new PreparedSourcePublicationFacts(
              stored.capabilityProfileRef(),
              descriptorReference(payloads.get("source-inventory.jsonl")),
              descriptorReference(payloads.get("source-preparation-result.json")),
              report.receipt().controls());
      return new SavedSourcePreparation(
          reportReference,
          sourceVersion,
          stored.request(),
          stored.result(),
          stored.assessment(),
          safelyRegistered ? stored.sourceRegistrationRef() : null,
          publicationFacts);
    } catch (IOException | RuntimeException failure) {
      throw new IllegalArgumentException(
          "prepared source publication cannot be reopened at " + check, failure);
    }
  }

  private static Map<String, VerifiedCanonicalPayload> exactPayloads(
      List<VerifiedCanonicalPayload> payloads) {
    Map<String, VerifiedCanonicalPayload> byName = new HashMap<>();
    for (VerifiedCanonicalPayload payload : payloads) {
      if (byName.put(payload.descriptor().fileName(), payload) != null) throw invalid();
    }
    if (!byName
        .keySet()
        .equals(
            java.util.Set.of(
                "source-input.json",
                "source-inventory.jsonl",
                "source-issues.jsonl",
                "source-preparation-result.json"))) throw invalid();
    return Map.copyOf(byName);
  }

  private static void verifyPayload(VerifiedCanonicalPayload payload, String type, String schema) {
    ArtifactDescriptor descriptor = payload.descriptor();
    if (!type.equals(descriptor.artifactType()) || !schema.equals(descriptor.schemaVersion()))
      throw invalid();
  }

  private static ArtifactReference descriptorReference(VerifiedCanonicalPayload payload) {
    ArtifactDescriptor descriptor = payload.descriptor();
    return new ArtifactReference(descriptor.artifactId(), descriptor.sha256());
  }

  private void verifyPrerequisiteModules(
      AnalysisStepPublicationReference reportReference,
      ReopenedAnalysisStepPublication report,
      ReopenedModulePublication publisher,
      PreparedSourceArchive.PublicationLinks links,
      ReopenedModulePublication m1,
      ReopenedModulePublication m2,
      PreparedSourceArchive.StoredCapture stored,
      boolean safelyRegistered) {
    AnalysisStepModuleAddress m1Address = moduleAddress(reportReference, 1, "request-admission");
    AnalysisStepModuleAddress m2Address = moduleAddress(reportReference, 2, "source-index");
    AnalysisStepModuleAddress m3Address = moduleAddress(reportReference, 3, "publish");
    List<ArtifactReference> m1Upstream =
        safelyRegistered
            ? sortedReferences(
                List.of(
                    stored.sourceRegistrationRef(),
                    stored.captureReceiptRef(),
                    stored.snapshotManifestRef()))
            : List.of();
    requireModuleReceipt(
        m1,
        links.requestAdmission(),
        m1Address,
        stored.controls(),
        ModuleCompletionStatus.SUCCEEDED,
        List.of(),
        m1Upstream);
    ArtifactReference m1Payload =
        requireModulePayload(
            m1,
            "admitted-source-request.json",
            SourcePreparationPublisher.ADMITTED_TYPE,
            SourcePreparationPublisher.ADMITTED_SCHEMA,
            SourcePreparationPublisher.admittedBody(stored.request(), stored));

    List<ArtifactReference> m2Upstream =
        safelyRegistered
            ? sortedReferences(List.of(m1Payload, stored.sourceRegistrationRef()))
            : List.of(m1Payload);
    requireModuleReceipt(
        m2,
        links.sourceIndex(),
        m2Address,
        stored.controls(),
        report.receipt().status(),
        report.receipt().gapRefs(),
        m2Upstream);
    ArtifactReference m2Payload =
        requireModulePayload(
            m2,
            "verified-source-index.json",
            SourcePreparationPublisher.INDEX_TYPE,
            SourcePreparationPublisher.INDEX_SCHEMA,
            SourcePreparationPublisher.indexBody(m1Payload, stored, safelyRegistered));

    requireModuleReceipt(
        publisher,
        publisher.reference(),
        m3Address,
        stored.controls(),
        report.receipt().status(),
        report.receipt().gapRefs(),
        sortedReferences(List.of(m1Payload, m2Payload)));
    if (!reportReference.address().equals(links.reportAddress())) {
      throw invalid();
    }
  }

  private static AnalysisStepModuleAddress moduleAddress(
      AnalysisStepPublicationReference reportReference, int moduleNumber, String moduleKey) {
    return new AnalysisStepModuleAddress(
        reportReference.address().runId(),
        AnalysisStepKey.VERIFIED_SOURCE_INVENTORY,
        moduleNumber,
        moduleKey);
  }

  private static void requireModuleReceipt(
      ReopenedModulePublication publication,
      ModulePublicationReference expectedReference,
      AnalysisStepModuleAddress expectedAddress,
      org.sourceanalysis.app.artifact.ArtifactControls expectedControls,
      ModuleCompletionStatus expectedStatus,
      List<String> expectedGaps,
      List<ArtifactReference> expectedUpstream) {
    if (!publication.reference().equals(expectedReference)
        || !"v3".equals(publication.receipt().moduleVersion())
        || !expectedAddress.equals(publication.receipt().address())
        || !expectedReference
            .moduleArtifactRoot()
            .equals(publication.receipt().moduleArtifactRoot())
        || !expectedReference.moduleReceiptId().equals(publication.receipt().moduleReceiptId())
        || !expectedControls.equals(publication.receipt().controls())
        || expectedStatus != publication.receipt().status()
        || !expectedGaps.equals(publication.receipt().gapRefs())
        || !expectedUpstream.equals(publication.receipt().upstreamArtifacts())
        || !publication
            .receipt()
            .payloadArtifacts()
            .equals(
                publication.payloads().stream()
                    .map(VerifiedCanonicalPayload::descriptor)
                    .toList())) {
      throw invalid();
    }
  }

  private ArtifactReference requireModulePayload(
      ReopenedModulePublication publication,
      String expectedFileName,
      String expectedType,
      String expectedSchema,
      ObjectNode expectedBody) {
    if (publication.payloads().size() != 1) {
      throw invalid();
    }
    VerifiedCanonicalPayload payload = publication.payloads().get(0);
    ArtifactDescriptor descriptor = payload.descriptor();
    if (!expectedFileName.equals(descriptor.fileName())
        || !publication.receipt().payloadArtifacts().equals(List.of(descriptor))) {
      throw invalid();
    }
    verifyPayload(payload, expectedType, expectedSchema);
    ObjectNode envelope = object(payload);
    if (!expectedType.equals(text(envelope, "artifactType"))
        || !expectedSchema.equals(text(envelope, "schemaVersion"))
        || !canonicalEquals(object(envelope.get("payload")), expectedBody)) {
      throw invalid();
    }
    return new ArtifactReference(descriptor.artifactId(), descriptor.sha256());
  }

  private boolean canonicalEquals(JsonNode actual, JsonNode expected) {
    return java.util.Arrays.equals(
        json.encodeCanonical(actual).copyToByteArray(),
        json.encodeCanonical(expected).copyToByteArray());
  }

  private static List<ArtifactReference> sortedReferences(List<ArtifactReference> references) {
    List<ArtifactReference> sorted = new ArrayList<>(references);
    sorted.sort(
        Comparator.comparing(
            reference -> reference.artifactId().value(), SourcePreparationReader::compareUtf8));
    for (int index = 1; index < sorted.size(); index++) {
      if (sorted.get(index - 1).equals(sorted.get(index))) {
        throw invalid();
      }
    }
    return List.copyOf(sorted);
  }

  private static int compareUtf8(String first, String second) {
    byte[] left = first.getBytes(StandardCharsets.UTF_8);
    byte[] right = second.getBytes(StandardCharsets.UTF_8);
    for (int index = 0; index < Math.min(left.length, right.length); index++) {
      int comparison =
          Integer.compare(Byte.toUnsignedInt(left[index]), Byte.toUnsignedInt(right[index]));
      if (comparison != 0) {
        return comparison;
      }
    }
    return Integer.compare(left.length, right.length);
  }

  private ObjectNode object(VerifiedCanonicalPayload payload) {
    JsonNode value = json.parseCanonical(payload.canonicalUtf8());
    if (!(value instanceof ObjectNode object)) throw invalid();
    return object;
  }

  private static ObjectNode object(JsonNode value) {
    if (!(value instanceof ObjectNode object)) {
      throw invalid();
    }
    return object;
  }

  private static void verifyStandaloneProjection(
      ObjectNode actual, String type, String schema, ObjectNode expectedBody) {
    if (!schema.equals(text(actual, "schemaVersion"))
        || !type.equals(text(actual, "artifactType"))) {
      throw invalid();
    }
    ObjectNode actualBody = actual.deepCopy();
    actualBody.remove("schemaVersion");
    actualBody.remove("artifactType");
    actualBody.remove("artifactId");
    if (!actualBody.equals(expectedBody)) {
      throw invalid();
    }
  }

  private List<ObjectNode> jsonlRows(VerifiedCanonicalPayload payload) {
    String jsonl =
        new String(
            payload.canonicalUtf8().copyToByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    if (jsonl.isEmpty()) {
      return List.of();
    }
    if (!jsonl.endsWith("\n")) {
      throw invalid();
    }
    List<ObjectNode> rows = new java.util.ArrayList<>();
    for (String line : jsonl.split("\\n", -1)) {
      if (line.isEmpty()) {
        continue;
      }
      JsonNode row =
          json.parseCanonical(
              org.sourceanalysis.app.artifact.ImmutableBytes.copyOf(
                  line.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      if (!(row instanceof ObjectNode object)) {
        throw invalid();
      }
      rows.add(object);
    }
    return List.copyOf(rows);
  }

  private void requireJsonlProjection(
      String label, List<ObjectNode> actual, List<ObjectNode> expected) {
    if (actual.size() != expected.size()) {
      throw new IllegalArgumentException(label + " JSONL row count differs");
    }
    for (int index = 0; index < actual.size(); index++) {
      ObjectNode actualRow = actual.get(index);
      ObjectNode expectedRow = expected.get(index);
      if (java.util.Arrays.equals(
          json.encodeCanonical(actualRow).copyToByteArray(),
          json.encodeCanonical(expectedRow).copyToByteArray())) {
        continue;
      }
      throw new IllegalArgumentException(label + " JSONL row " + index + " canonical bytes differ");
    }
  }

  private static String text(ObjectNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || !value.isTextual()) throw invalid();
    return value.textValue();
  }

  private static org.sourceanalysis.app.artifact.ArtifactReference reference(
      ObjectNode node, String field) {
    JsonNode value = node.get(field);
    if (!(value instanceof ObjectNode ref)) throw invalid();
    return new org.sourceanalysis.app.artifact.ArtifactReference(
        org.sourceanalysis.app.artifact.ArtifactId.parse(text(ref, "artifactId")),
        org.sourceanalysis.app.artifact.Sha256Digest.parse(text(ref, "sha256")));
  }

  private static boolean isNull(ObjectNode node, String field) {
    JsonNode value = node.get(field);
    return value != null && value.isNull();
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("prepared source publication cannot be reopened");
  }
}
