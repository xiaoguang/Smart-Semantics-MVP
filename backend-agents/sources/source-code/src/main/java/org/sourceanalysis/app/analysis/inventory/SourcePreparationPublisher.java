package org.sourceanalysis.app.analysis.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepInstallRequest;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublisherModuleProvenance;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyKey;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepPayload;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicy;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledAnalysisStepPublication;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.capture.preparation.CapturedSourcePreparation;
import org.sourceanalysis.app.capture.preparation.PreparedSourceArchive;

/** Installs closed prepared-source facts through v3 M1/M2/M3 and the receipt-last Step01 store. */
public final class SourcePreparationPublisher {

  static final String INPUT_TYPE = "SOURCE_PREPARATION_INPUT";
  static final String INPUT_SCHEMA = "source-preparation-input-v1";
  static final String INVENTORY_TYPE = "SOURCE_PREPARATION_INVENTORY";
  static final String INVENTORY_SCHEMA = "source-preparation-inventory-v1";
  static final String ISSUES_TYPE = "SOURCE_PREPARATION_ISSUES";
  static final String ISSUES_SCHEMA = "source-preparation-issues-v1";
  static final String RESULT_TYPE = "SOURCE_PREPARATION_RESULT";
  static final String RESULT_SCHEMA = "source-preparation-result-v1";
  static final String ADMITTED_TYPE = "VERIFIED_SOURCE_INVENTORY_ADMITTED_SOURCE_REQUEST";
  static final String ADMITTED_SCHEMA = "verified-source-inventory-admitted-source-request-v3";
  static final String INDEX_TYPE = "VERIFIED_SOURCE_INVENTORY_VERIFIED_SOURCE_INDEX";
  static final String INDEX_SCHEMA = "verified-source-inventory-verified-source-index-v3";
  static final String VERSION = "v3";

  private static final Comparator<String> UTF8_ORDER = SourcePreparationPublisher::compareUtf8;

  private final CanonicalModuleArtifactStore modules;
  private final CanonicalAnalysisStepArtifactStore steps;
  private final PreparedSourceArchive archive;
  private final CanonicalArtifactPolicyRegistry policies;
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  public SourcePreparationPublisher(
      CanonicalModuleArtifactStore modules,
      CanonicalAnalysisStepArtifactStore steps,
      PreparedSourceArchive archive,
      CanonicalArtifactPolicyRegistry policies) {
    this.modules = Objects.requireNonNull(modules, "canonical module artifact store");
    this.steps = Objects.requireNonNull(steps, "canonical analysis-step artifact store");
    this.archive = Objects.requireNonNull(archive, "prepared source archive");
    this.policies = Objects.requireNonNull(policies, "canonical artifact policy registry");
  }

  /**
   * Publishes exactly one closed capture; it never opens the origin path carried by the request.
   */
  public SavedSourcePreparation publish(AnalysisRunId runId, CapturedSourcePreparation capture)
      throws IOException {
    Objects.requireNonNull(runId, "analysis run ID");
    Objects.requireNonNull(capture, "captured source preparation");
    SourcePreparationAssessment assessment =
        SourcePreparationReadinessEvaluator.assess(capture.result());
    ArtifactControls controls = archive.controlsFor(capture, policies.reference());
    PreparedSourceArchive.StoredCapture stored = archive.save(capture, assessment, controls);
    if (!stored.controls().equals(controls)) {
      throw new IOException("prepared source controls changed before publication");
    }
    SourcePreparationAssessment savedAssessment = stored.assessment();
    boolean safelyRegistered = safelyRegistered(stored);

    AnalysisStepModuleAddress m1Address = address(runId, 1, "request-admission");
    CanonicalModulePayload m1Payload =
        modulePayload(
            "admitted-source-request.json",
            ADMITTED_TYPE,
            ADMITTED_SCHEMA,
            m1Address,
            controls,
            m1Upstream(stored, safelyRegistered),
            ModuleCompletionStatus.SUCCEEDED,
            List.of(),
            admittedBody(stored.request(), stored));
    InstalledModulePublication m1 =
        modules.install(
            new ModuleInstallRequest(
                m1Address,
                VERSION,
                m1Upstream(stored, safelyRegistered),
                controls,
                ModuleCompletionStatus.SUCCEEDED,
                List.of(),
                List.of(m1Payload)));

    List<String> gapRefs = gapRefs(stored.result());
    ModuleCompletionStatus resultStatus =
        gapRefs.isEmpty()
            ? ModuleCompletionStatus.SUCCEEDED
            : ModuleCompletionStatus.SUCCEEDED_WITH_GAPS;
    ArtifactReference m1Reference = descriptorReference(m1);
    AnalysisStepModuleAddress m2Address = address(runId, 2, "source-index");
    List<ArtifactReference> m2Upstream =
        safelyRegistered
            ? sortedReferences(List.of(m1Reference, stored.sourceRegistrationRef()))
            : List.of(m1Reference);
    CanonicalModulePayload m2Payload =
        modulePayload(
            "verified-source-index.json",
            INDEX_TYPE,
            INDEX_SCHEMA,
            m2Address,
            controls,
            m2Upstream,
            resultStatus,
            gapRefs,
            indexBody(m1Reference, stored, safelyRegistered));
    InstalledModulePublication m2 =
        modules.install(
            new ModuleInstallRequest(
                m2Address,
                VERSION,
                m2Upstream,
                controls,
                resultStatus,
                gapRefs,
                List.of(m2Payload)));

    archive.savePublicationLinks(
        stored.sourceVersionId(),
        new AnalysisStepPublicationAddress(runId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY),
        m1.reference(),
        m2.reference());
    ArtifactReference m2Reference = descriptorReference(m2);
    AnalysisStepModuleAddress m3Address = address(runId, 3, "publish");
    List<ArtifactReference> m3Upstream = sortedReferences(List.of(m1Reference, m2Reference));
    List<CanonicalModulePayload> publicPayloads =
        publicPayloads(stored, m3Address, controls, m3Upstream, resultStatus, gapRefs);
    InstalledModulePublication m3 =
        modules.install(
            new ModuleInstallRequest(
                m3Address, VERSION, m3Upstream, controls, resultStatus, gapRefs, publicPayloads));

    List<CanonicalAnalysisStepPayload> stepPayloads =
        publicPayloads.stream().map(SourcePreparationPublisher::stepPayload).toList();
    InstalledAnalysisStepPublication installedStep =
        steps.install(
            new AnalysisStepInstallRequest(
                new AnalysisStepPublicationAddress(
                    runId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY),
                new AnalysisStepPublisherModuleProvenance(m3.reference()),
                List.of(),
                controls,
                resultStatus,
                gapRefs,
                stepPayloads,
                null));
    PreparedSourceReference sourceVersion =
        safelyRegistered
            ? new PreparedSourceReference(
                stored.sourceVersionId(),
                installedStep.reference(),
                stored.schemaBundleRef(),
                policies.reference())
            : null;
    PreparedSourcePublicationFacts publicationFacts =
        new PreparedSourcePublicationFacts(
            stored.capabilityProfileRef(),
            descriptorReference(
                installedStep, "source-inventory.jsonl", INVENTORY_TYPE, INVENTORY_SCHEMA),
            descriptorReference(
                installedStep, "source-preparation-result.json", RESULT_TYPE, RESULT_SCHEMA),
            controls);
    return new SavedSourcePreparation(
        installedStep.reference(),
        sourceVersion,
        stored.request(),
        stored.result(),
        savedAssessment,
        safelyRegistered ? stored.sourceRegistrationRef() : null,
        publicationFacts);
  }

  private List<CanonicalModulePayload> publicPayloads(
      PreparedSourceArchive.StoredCapture stored,
      AnalysisStepModuleAddress address,
      ArtifactControls controls,
      List<ArtifactReference> upstream,
      ModuleCompletionStatus status,
      List<String> gaps) {
    ObjectNode input = inputBody(stored);
    ObjectNode result = resultBody(stored);
    CanonicalModulePayload sourceInput =
        standalonePayload("source-input.json", INPUT_TYPE, INPUT_SCHEMA, input);
    CanonicalModulePayload inventory =
        jsonlPayload(
            "source-inventory.jsonl",
            INVENTORY_TYPE,
            INVENTORY_SCHEMA,
            inventoryRows(stored.result()));
    CanonicalModulePayload issues =
        jsonlPayload("source-issues.jsonl", ISSUES_TYPE, ISSUES_SCHEMA, issueRows(stored.result()));
    CanonicalModulePayload sourceResult =
        standalonePayload("source-preparation-result.json", RESULT_TYPE, RESULT_SCHEMA, result);
    return List.of(sourceInput, inventory, issues, sourceResult);
  }

  private CanonicalModulePayload modulePayload(
      String fileName,
      String type,
      String schema,
      AnalysisStepModuleAddress address,
      ArtifactControls controls,
      List<ArtifactReference> upstream,
      ModuleCompletionStatus status,
      List<String> gaps,
      ObjectNode body) {
    CanonicalArtifactPolicy policy = policies.resolve(new ArtifactPolicyKey(type, schema));
    ObjectNode withoutId = JsonNodeFactory.instance.objectNode();
    withoutId.put("schemaVersion", schema);
    withoutId.put("artifactType", type);
    withoutId.set("producer", producer(address));
    withoutId.set("upstreamArtifacts", references(upstream));
    withoutId.set("controls", controlsNode(controls));
    withoutId.set("completion", completionNode(status, gaps));
    withoutId.set("payload", body);
    String id =
        policy.artifactIdPrefix()
            + ":"
            + sha256(
                concatenate(
                    frame("canonical-module-artifact-id-v1"),
                    frame(schema),
                    frame(type),
                    frame(json.encodeCanonical(withoutId).copyToByteArray())));
    ObjectNode envelope = withoutId.deepCopy();
    envelope.put("artifactId", id);
    return new CanonicalModulePayload(
        fileName,
        type,
        schema,
        ArtifactId.parse(id),
        CanonicalMediaType.APPLICATION_JSON,
        json.encodeCanonical(envelope));
  }

  private CanonicalModulePayload standalonePayload(
      String fileName, String type, String schema, ObjectNode body) {
    CanonicalArtifactPolicy policy = policies.resolve(new ArtifactPolicyKey(type, schema));
    ObjectNode withoutId = body.deepCopy();
    withoutId.put("schemaVersion", schema);
    withoutId.put("artifactType", type);
    String id =
        policy.artifactIdPrefix()
            + ":"
            + sha256(
                concatenate(
                    frame("canonical-standalone-json-artifact-id-v1"),
                    frame(schema),
                    frame(type),
                    frame(json.encodeCanonical(withoutId).copyToByteArray())));
    withoutId.put("artifactId", id);
    return new CanonicalModulePayload(
        fileName,
        type,
        schema,
        ArtifactId.parse(id),
        CanonicalMediaType.APPLICATION_JSON,
        json.encodeCanonical(withoutId));
  }

  private CanonicalModulePayload jsonlPayload(
      String fileName, String type, String schema, List<ObjectNode> rows) {
    CanonicalArtifactPolicy policy = policies.resolve(new ArtifactPolicyKey(type, schema));
    StringBuilder text = new StringBuilder();
    for (ObjectNode row : rows) {
      text.append(new String(json.encodeCanonical(row).copyToByteArray(), StandardCharsets.UTF_8))
          .append('\n');
    }
    byte[] bytes = text.toString().getBytes(StandardCharsets.UTF_8);
    String id =
        policy.artifactIdPrefix()
            + ":"
            + sha256(
                concatenate(
                    frame("canonical-jsonl-artifact-id-v1"),
                    frame(schema),
                    frame(type),
                    frame(bytes)));
    return new CanonicalModulePayload(
        fileName,
        type,
        schema,
        ArtifactId.parse(id),
        CanonicalMediaType.APPLICATION_X_NDJSON,
        ImmutableBytes.copyOf(bytes));
  }

  static ObjectNode admittedBody(
      SourcePreparationRequest request, PreparedSourceArchive.StoredCapture stored) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.set("preparationRequestRef", referenceNode(stored.preparationRequestRef()));
    body.put("operation", request.operation().name());
    body.set("origin", publicOrigin(request.origin()));
    body.set("basePreparation", preparedReference(request.basePreparation()));
    body.set("targets", targets(request.targets()));
    body.set("declaredExclusions", targets(request.declaredExclusions()));
    body.set("effectiveExclusions", targets(request.effectiveExclusions()));
    body.set("limits", limits(request.limits()));
    body.set("policyRef", referenceNode(request.policyRef()));
    boolean safelyRegistered = safelyRegistered(stored);
    body.set(
        "sourceRegistrationRef",
        nullableReferenceNode(safelyRegistered ? stored.sourceRegistrationRef() : null));
    body.set(
        "captureReceiptRef",
        nullableReferenceNode(safelyRegistered ? stored.captureReceiptRef() : null));
    body.set(
        "snapshotManifestRef",
        nullableReferenceNode(safelyRegistered ? stored.snapshotManifestRef() : null));
    body.set("preparationProfileRef", referenceNode(stored.preparationProfileRef()));
    body.set("preparationToolchainRef", referenceNode(stored.preparationToolchainRef()));
    body.set("schemaBundleRef", referenceNode(stored.schemaBundleRef()));
    body.set("resourceBudgetRef", referenceNode(stored.resourceBudgetRef()));
    body.set("capabilityProfileRef", referenceNode(stored.capabilityProfileRef()));
    return body;
  }

  static ObjectNode indexBody(
      ArtifactReference requestArtifact,
      PreparedSourceArchive.StoredCapture stored,
      boolean safelyRegistered) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.set("requestArtifactRef", referenceNode(requestArtifact));
    if (safelyRegistered) body.put("sourceVersionId", stored.sourceVersionId().value());
    else body.putNull("sourceVersionId");
    body.put("inspectionStatus", stored.result().inspectionStatus().name());
    body.put("enumerationComplete", stored.result().enumerationComplete());
    body.put("readiness", stored.assessment().readiness().name());
    body.set("summary", summary(stored.assessment().summary()));
    ArrayNode entries = body.putArray("entries");
    inventoryRows(stored.result()).forEach(entries::add);
    ArrayNode issues = body.putArray("issues");
    issueRows(stored.result()).forEach(issues::add);
    ArrayNode unknown = body.putArray("unknownSubtrees");
    stored.result().unknownSubtrees().stream().sorted(UTF8_ORDER).forEach(unknown::add);
    body.set("unmatchedExclusions", targets(stored.result().unmatchedExclusions()));
    return body;
  }

  static ObjectNode inputBody(PreparedSourceArchive.StoredCapture stored) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("operation", stored.request().operation().name());
    body.set("origin", publicOrigin(stored.request().origin()));
    body.put("sourceVersionId", stored.sourceVersionId().value());
    boolean safelyRegistered = safelyRegistered(stored);
    body.set(
        "sourceRegistrationRef",
        nullableReferenceNode(safelyRegistered ? stored.sourceRegistrationRef() : null));
    body.set(
        "captureReceiptRef",
        nullableReferenceNode(safelyRegistered ? stored.captureReceiptRef() : null));
    body.set(
        "snapshotManifestRef",
        nullableReferenceNode(safelyRegistered ? stored.snapshotManifestRef() : null));
    body.set("preparationProfileRef", referenceNode(stored.preparationProfileRef()));
    body.set("preparationToolchainRef", referenceNode(stored.preparationToolchainRef()));
    body.set("schemaBundleRef", referenceNode(stored.schemaBundleRef()));
    body.set("resourceBudgetRef", referenceNode(stored.resourceBudgetRef()));
    body.set("capabilityProfileRef", referenceNode(stored.capabilityProfileRef()));
    return body;
  }

  static ObjectNode resultBody(PreparedSourceArchive.StoredCapture stored) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("operation", stored.request().operation().name());
    body.set("basePreparation", preparedReference(stored.request().basePreparation()));
    if (safelyRegistered(stored)) body.put("sourceVersionId", stored.sourceVersionId().value());
    else body.putNull("sourceVersionId");
    body.put("inspectionStatus", stored.result().inspectionStatus().name());
    body.put("readiness", stored.assessment().readiness().name());
    body.set("summary", summary(stored.assessment().summary()));
    ArrayNode unknown = body.putArray("unknownSubtrees");
    stored.result().unknownSubtrees().stream().sorted(UTF8_ORDER).forEach(unknown::add);
    body.set("unmatchedExclusions", targets(stored.result().unmatchedExclusions()));
    return body;
  }

  static List<ObjectNode> inventoryRows(SourcePreparationResult result) {
    return result.entries().stream()
        .sorted(Comparator.comparing(SourceEntry::relativePath, UTF8_ORDER))
        .map(SourcePreparationPublisher::entry)
        .toList();
  }

  static List<ObjectNode> issueRows(SourcePreparationResult result) {
    return result.issues().stream()
        .sorted(Comparator.comparing(SourceIssue::issueId, UTF8_ORDER))
        .map(SourcePreparationPublisher::issue)
        .toList();
  }

  private static ObjectNode entry(SourceEntry source) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("relativePath", source.relativePath());
    node.put("entryKind", source.entryKind().name());
    node.put("disposition", source.disposition().name());
    if (source.sizeBytes() == null) node.putNull("sizeBytes");
    else node.put("sizeBytes", source.sizeBytes());
    if (source.sha256() == null) node.putNull("sha256");
    else node.put("sha256", source.sha256().value());
    if (source.blobRef() == null) node.putNull("blobRef");
    else node.set("blobRef", referenceNode(source.blobRef()));
    if (source.fileId() == null) node.putNull("fileId");
    else node.put("fileId", source.fileId().value());
    if (source.textEncoding() == null) node.putNull("textEncoding");
    else node.put("textEncoding", source.textEncoding());
    node.set("originAttributes", originAttributes(source.originAttributes()));
    if (source.observations() == null) {
      node.putNull("observations");
    } else {
      ObjectNode observations = node.putObject("observations");
      if (source.observations().before() == null) observations.putNull("before");
      else observations.set("before", observation(source.observations().before()));
      if (source.observations().after() == null) observations.putNull("after");
      else observations.set("after", observation(source.observations().after()));
    }
    ArrayNode issueIds = node.putArray("issueIds");
    source.issueIds().forEach(issueIds::add);
    if (source.inheritedFrom() == null) {
      node.putNull("inheritedFrom");
      node.putNull("inheritedFileId");
    } else {
      node.put("inheritedFrom", source.inheritedFrom().baseSourceVersion().value());
      if (source.inheritedFrom().fileId() == null) node.putNull("inheritedFileId");
      else node.put("inheritedFileId", source.inheritedFrom().fileId().value());
    }
    if (source.exclusion() == null) {
      node.putNull("exclusion");
    } else {
      ObjectNode exclusion = node.putObject("exclusion");
      exclusion.put("category", source.exclusion().category());
      exclusion.put("decision", source.exclusion().decision().name());
      exclusion.put("coveredPath", source.exclusion().coveredPath());
    }
    return node;
  }

  private static ObjectNode issue(SourceIssue issue) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("issueId", issue.issueId());
    node.put("code", issue.code().name());
    node.put("category", issue.category().name());
    node.put("scope", issue.scope().name());
    if (issue.relativePath() == null) node.putNull("relativePath");
    else node.put("relativePath", issue.relativePath());
    node.put("operation", issue.operation().name());
    node.put("message", issue.message());
    if (issue.expected() == null) node.putNull("expected");
    else node.set("expected", observation(issue.expected()));
    if (issue.observed() == null) node.putNull("observed");
    else node.set("observed", observation(issue.observed()));
    node.put("resolution", issue.resolution().name());
    ArrayNode actions = node.putArray("allowedActions");
    issue.allowedActions().stream().map(Enum::name).sorted(UTF8_ORDER).forEach(actions::add);
    if (issue.diagnosticRef() == null) node.putNull("diagnosticRef");
    else node.set("diagnosticRef", referenceNode(issue.diagnosticRef()));
    return node;
  }

  private static ObjectNode observation(SourceObservation observation) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    if (observation.sizeBytes() == null) node.putNull("sizeBytes");
    else node.put("sizeBytes", observation.sizeBytes());
    if (observation.sha256() == null) node.putNull("sha256");
    else node.put("sha256", observation.sha256().value());
    if (observation.sourceIdentity() == null) node.putNull("sourceIdentity");
    else node.put("sourceIdentity", observation.sourceIdentity().value());
    if (observation.modifiedAt() == null) node.putNull("modifiedAt");
    else node.put("modifiedAt", observation.modifiedAt().toString());
    if (observation.fileKey() == null) node.putNull("fileKey");
    else node.put("fileKey", observation.fileKey());
    return node;
  }

  static List<String> gapRefs(SourcePreparationResult result) {
    return result.issues().stream()
        .filter(
            issue ->
                issue.resolution() != SourceIssue.Resolution.RESOLVED_BY_REFRESH
                    && issue.resolution() != SourceIssue.Resolution.RESOLVED_BY_EXCLUSION)
        .map(issue -> "source-issue:" + issue.issueId())
        .distinct()
        .sorted(UTF8_ORDER)
        .toList();
  }

  private static AnalysisStepModuleAddress address(AnalysisRunId runId, int number, String key) {
    return new AnalysisStepModuleAddress(
        runId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, number, key);
  }

  private static boolean safelyRegistered(PreparedSourceArchive.StoredCapture stored) {
    return stored.assessment().readiness() != SourcePreparationReadiness.BLOCKED;
  }

  private static List<ArtifactReference> m1Upstream(
      PreparedSourceArchive.StoredCapture stored, boolean safelyRegistered) {
    if (!safelyRegistered) {
      return List.of();
    }
    return sortedReferences(
        List.of(
            stored.sourceRegistrationRef(),
            stored.captureReceiptRef(),
            stored.snapshotManifestRef()));
  }

  private static CanonicalAnalysisStepPayload stepPayload(CanonicalModulePayload payload) {
    return new CanonicalAnalysisStepPayload(
        payload.fileName(),
        payload.artifactType(),
        payload.schemaVersion(),
        payload.artifactId(),
        payload.mediaType(),
        payload.canonicalUtf8());
  }

  private static ArtifactReference descriptorReference(InstalledModulePublication publication) {
    if (publication.artifactDescriptors().size() != 1)
      throw new IllegalArgumentException("source module needs one payload");
    var descriptor = publication.artifactDescriptors().get(0);
    return new ArtifactReference(descriptor.artifactId(), descriptor.sha256());
  }

  private static ArtifactReference descriptorReference(
      InstalledAnalysisStepPublication publication,
      String fileName,
      String artifactType,
      String schemaVersion) {
    var matches =
        publication.semanticArtifactDescriptors().stream()
            .filter(descriptor -> fileName.equals(descriptor.fileName()))
            .toList();
    if (matches.size() != 1
        || !artifactType.equals(matches.get(0).artifactType())
        || !schemaVersion.equals(matches.get(0).schemaVersion())) {
      throw new IllegalArgumentException("prepared source step payload is missing or invalid");
    }
    var descriptor = matches.get(0);
    return new ArtifactReference(descriptor.artifactId(), descriptor.sha256());
  }

  private static List<ArtifactReference> sortedReferences(List<ArtifactReference> references) {
    List<ArtifactReference> sorted = new ArrayList<>(references);
    sorted.sort(Comparator.comparing(reference -> reference.artifactId().value(), UTF8_ORDER));
    for (int index = 1; index < sorted.size(); index++)
      if (sorted.get(index - 1).equals(sorted.get(index)))
        throw new IllegalArgumentException("source publication upstream references must be unique");
    return List.copyOf(sorted);
  }

  private static ObjectNode producer(AnalysisStepModuleAddress address) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    ObjectNode location = node.putObject("address");
    location.put("kind", "ANALYSIS_STEP");
    location.put("runId", address.runId().value());
    location.put("analysisStepKey", address.analysisStepKey().wireValue());
    location.put("moduleNumber", address.moduleNumber());
    location.put("moduleKey", address.moduleKey());
    node.put("moduleVersion", VERSION);
    return node;
  }

  private static JsonNode preparedReference(PreparedSourceReference reference) {
    if (reference == null) return JsonNodeFactory.instance.nullNode();
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("sourceVersionId", reference.sourceVersionId().value());
    ObjectNode publication = node.putObject("publication");
    publication.put("runId", reference.publication().address().runId().value());
    publication.put(
        "analysisStepKey", reference.publication().address().analysisStepKey().wireValue());
    publication.put("root", reference.publication().analysisStepArtifactRoot().value());
    publication.put("receiptId", reference.publication().analysisStepReceiptId().value());
    publication.put("receiptSha256", reference.publication().analysisStepReceiptSha256().value());
    node.set("schemaBundleRef", referenceNode(reference.schemaBundleRef()));
    ObjectNode policy = node.putObject("artifactPolicyRegistryRef");
    policy.put("artifactId", reference.artifactPolicyRegistryRef().artifactId().value());
    policy.put("sha256", reference.artifactPolicyRegistryRef().sha256().value());
    return node;
  }

  private static ArrayNode references(List<ArtifactReference> refs) {
    ArrayNode nodes = JsonNodeFactory.instance.arrayNode();
    refs.forEach(ref -> nodes.add(referenceNode(ref)));
    return nodes;
  }

  private static ObjectNode referenceNode(ArtifactReference ref) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("artifactId", ref.artifactId().value());
    node.put("sha256", ref.sha256().value());
    return node;
  }

  private static JsonNode nullableReferenceNode(ArtifactReference ref) {
    return ref == null ? JsonNodeFactory.instance.nullNode() : referenceNode(ref);
  }

  private static ObjectNode controlsNode(ArtifactControls controls) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("toolchainSha256", controls.toolchainSha256().value());
    node.put("profileSha256", controls.profileSha256().value());
    node.put("schemaBundleSha256", controls.schemaBundleSha256().value());
    if (controls.promptBundleSha256() == null) node.putNull("promptBundleSha256");
    else node.put("promptBundleSha256", controls.promptBundleSha256().value());
    node.set(
        "artifactPolicyRegistryRef",
        referenceNode(
            new ArtifactReference(
                controls.artifactPolicyRegistryRef().artifactId(),
                controls.artifactPolicyRegistryRef().sha256())));
    return node;
  }

  private static ObjectNode completionNode(ModuleCompletionStatus status, List<String> gaps) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("status", status.name());
    ArrayNode values = node.putArray("gapRefs");
    gaps.forEach(values::add);
    node.putNull("failureRef");
    return node;
  }

  private static ObjectNode publicOrigin(SourceOrigin origin) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("kind", origin.kind().name());
    node.put("logicalIdentity", origin.logicalIdentity());
    if (origin instanceof GitCommitSourceOrigin git) node.put("commitId", git.commitId());
    else node.putNull("commitId");
    return node;
  }

  private static ArrayNode targets(List<SourcePreparationTarget> values) {
    ArrayNode nodes = JsonNodeFactory.instance.arrayNode();
    values.stream()
        .sorted(Comparator.comparing(SourcePreparationTarget::relativePath, UTF8_ORDER))
        .forEach(
            target -> {
              ObjectNode node = nodes.addObject();
              node.put("relativePath", target.relativePath());
              node.put("kind", target.kind().name());
            });
    return nodes;
  }

  private static ObjectNode limits(SourcePreparationLimits limits) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("maxFiles", limits.maxFiles());
    node.put("maxTotalBytes", limits.maxTotalBytes());
    return node;
  }

  private static ObjectNode summary(SourcePreparationSummary summary) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    if (summary.totalRegularFiles() == null) node.putNull("totalRegularFiles");
    else node.put("totalRegularFiles", summary.totalRegularFiles());
    node.put("discoveredRegularFiles", summary.discoveredRegularFiles());
    node.put("verifiedTextFiles", summary.verifiedTextFiles());
    node.put("verifiedMediaFiles", summary.verifiedMediaFiles());
    node.put("excludedKnownFiles", summary.excludedKnownFiles());
    node.put("unavailableKnownFiles", summary.unavailableKnownFiles());
    node.put("uncheckedKnownFiles", summary.uncheckedKnownFiles());
    node.put("directoryEntries", summary.directoryEntries());
    node.put("symlinkEntries", summary.symlinkEntries());
    node.put("submoduleEntries", summary.submoduleEntries());
    node.put("enumerationComplete", summary.enumerationComplete());
    ArrayNode unknown = node.putArray("unknownSubtrees");
    summary.unknownSubtrees().forEach(unknown::add);
    node.set("unmatchedExclusions", targets(summary.unmatchedExclusions()));
    return node;
  }

  private static ObjectNode originAttributes(SourceOriginAttributes attributes) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("kind", attributes.kind().name());
    if (attributes.gitMode() == null) node.putNull("gitMode");
    else node.put("gitMode", attributes.gitMode());
    if (attributes.gitBlobObjectId() == null) node.putNull("gitBlobObjectId");
    else node.put("gitBlobObjectId", attributes.gitBlobObjectId());
    if (attributes.executable() == null) node.putNull("executable");
    else node.put("executable", attributes.executable());
    return node;
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] value) {
    return ByteBuffer.allocate(Long.BYTES + value.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(value.length)
        .put(value)
        .array();
  }

  private static byte[] concatenate(byte[]... values) {
    int length = 0;
    for (byte[] value : values) length = Math.addExact(length, value.length);
    byte[] result = new byte[length];
    int offset = 0;
    for (byte[] value : values) {
      System.arraycopy(value, 0, result, offset, value.length);
      offset += value.length;
    }
    return result;
  }

  private static String sha256(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 unavailable", unavailable);
    }
  }

  private static int compareUtf8(String first, String second) {
    byte[] left = first.getBytes(StandardCharsets.UTF_8);
    byte[] right = second.getBytes(StandardCharsets.UTF_8);
    for (int index = 0; index < Math.min(left.length, right.length); index++) {
      int comparison =
          Integer.compare(Byte.toUnsignedInt(left[index]), Byte.toUnsignedInt(right[index]));
      if (comparison != 0) return comparison;
    }
    return Integer.compare(left.length, right.length);
  }
}
