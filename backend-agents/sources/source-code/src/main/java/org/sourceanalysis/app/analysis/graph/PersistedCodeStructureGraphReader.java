package org.sourceanalysis.app.analysis.graph;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/** Reopens the only M1 module artifact that may provide a code-structure graph to M2. */
public final class PersistedCodeStructureGraphReader {

  private static final String FILE_NAME = "code-structure-draft.json";
  private static final String ARTIFACT_TYPE = "PROGRAM_GRAPHS_CODE_STRUCTURE_DRAFT";
  private static final String MODULE_VERSION = "v1";

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalJsonCodec canonicalJson;

  /** Creates the reader with the canonical module-store boundary and no filesystem-path input. */
  public PersistedCodeStructureGraphReader(CanonicalModuleArtifactStore moduleArtifacts) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
    canonicalJson = new CanonicalJsonCodec();
  }

  /**
   * Reopens and proves one M1 draft against exactly the source/discovery aggregate that M2 will
   * consume.
   */
  public ReopenedCodeStructureGraph reopen(
      CodeStructureGraphDraftReference reference,
      ReopenedProgramGraphInputs sameInputs,
      ArtifactReference expectedGraphProfileRef) {
    try {
      Objects.requireNonNull(reference, "code structure graph reference");
      Objects.requireNonNull(sameInputs, "reopened program graph inputs");
      Objects.requireNonNull(expectedGraphProfileRef, "expected graph profile reference");
      ReopenedModulePublication publication = moduleArtifacts.reopen(reference.publication());
      requireModuleAddress(publication, reference);
      VerifiedCanonicalPayload payload = requirePayload(publication);
      JsonNode envelope = canonicalJson.parseCanonical(payload.canonicalUtf8());
      ProgramGraphInputBasis expected =
          ProgramGraphInputBasis.from(
              sameInputs.source(),
              sameInputs.discovery().codeStructureDiscovery(),
              expectedGraphProfileRef);
      requireReceiptLineage(publication, expected);
      requireEnvelopeLineage(envelope, publication, payload, expected);
      CodeStructureGraphDraft draft = parseDraft(envelope.get("payload"));
      if (!draft.snapshotId().equals(expected.snapshotId())
          || !draft.applicationProfileId().equals(expected.applicationProfileId())
          || !draft.entryIds().equals(expected.entryIds())
          || !draft.graphProfileRef().equals(expected.graphProfileRef())) {
        throw broken();
      }
      return new VerifiedReopenedCodeStructureGraph(
          reference,
          new ArtifactReference(payload.descriptor().artifactId(), payload.descriptor().sha256()),
          draft,
          expected);
    } catch (GraphReferenceException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw broken();
    }
  }

  private static void requireModuleAddress(
      ReopenedModulePublication publication, CodeStructureGraphDraftReference reference) {
    if (!publication.reference().equals(reference.publication())
        || !publication.receipt().address().equals(reference.publication().address())
        || !publication.receipt().moduleVersion().equals(MODULE_VERSION)
        || !(publication.receipt().address() instanceof AnalysisStepModuleAddress address)
        || address.analysisStepKey() != AnalysisStepKey.PROGRAM_GRAPHS
        || address.moduleNumber() != 1
        || !address.moduleKey().equals("code-structure")
        || (publication.receipt().status() != ModuleCompletionStatus.SUCCEEDED
            && publication.receipt().status() != ModuleCompletionStatus.SUCCEEDED_WITH_GAPS)) {
      throw broken();
    }
  }

  private static VerifiedCanonicalPayload requirePayload(ReopenedModulePublication publication) {
    if (publication.payloads().size() != 1) {
      throw broken();
    }
    VerifiedCanonicalPayload payload = publication.payloads().get(0);
    if (!payload.descriptor().fileName().equals(FILE_NAME)
        || !payload.descriptor().artifactType().equals(ARTIFACT_TYPE)
        || !payload.descriptor().schemaVersion().equals(CodeStructureGraphDraft.SCHEMA_VERSION)
        || payload.descriptor().mediaType() != CanonicalMediaType.APPLICATION_JSON
        || !publication.receipt().payloadArtifacts().equals(List.of(payload.descriptor()))) {
      throw broken();
    }
    return payload;
  }

  private static void requireReceiptLineage(
      ReopenedModulePublication publication, ProgramGraphInputBasis expected) {
    if (!publication.receipt().controls().equals(expected.controls())
        || !publication.receipt().upstreamArtifacts().equals(expectedUpstream(expected))) {
      throw broken();
    }
  }

  private static void requireEnvelopeLineage(
      JsonNode envelope,
      ReopenedModulePublication publication,
      VerifiedCanonicalPayload payload,
      ProgramGraphInputBasis expected) {
    requireObjectFields(
        envelope,
        Set.of(
            "schemaVersion",
            "artifactType",
            "artifactId",
            "producer",
            "upstreamArtifacts",
            "controls",
            "completion",
            "payload"));
    if (!text(envelope, "schemaVersion").equals(CodeStructureGraphDraft.SCHEMA_VERSION)
        || !text(envelope, "artifactType").equals(ARTIFACT_TYPE)
        || !text(envelope, "artifactId").equals(payload.descriptor().artifactId().value())) {
      throw broken();
    }
    requireProducer(envelope.get("producer"), publication);
    if (!references(envelope.get("upstreamArtifacts")).equals(expectedUpstream(expected))
        || !controls(envelope.get("controls")).equals(expected.controls())) {
      throw broken();
    }
    requireCompletion(envelope.get("completion"), publication);
  }

  private static void requireProducer(JsonNode producer, ReopenedModulePublication publication) {
    requireObjectFields(producer, Set.of("address", "moduleVersion"));
    if (!text(producer, "moduleVersion").equals(MODULE_VERSION)) {
      throw broken();
    }
    JsonNode address = producer.get("address");
    requireObjectFields(
        address, Set.of("kind", "runId", "analysisStepKey", "moduleNumber", "moduleKey"));
    AnalysisStepModuleAddress expected =
        (AnalysisStepModuleAddress) publication.receipt().address();
    if (!text(address, "kind").equals("ANALYSIS_STEP")
        || !text(address, "runId").equals(expected.runId().value())
        || !text(address, "analysisStepKey").equals(expected.analysisStepKey().wireValue())
        || integer(address, "moduleNumber") != expected.moduleNumber()
        || !text(address, "moduleKey").equals(expected.moduleKey())) {
      throw broken();
    }
  }

  private static void requireCompletion(
      JsonNode completion, ReopenedModulePublication publication) {
    requireObjectFields(completion, Set.of("status", "gapRefs", "failureRef"));
    if (!text(completion, "status").equals(publication.receipt().status().name())
        || !strings(completion.get("gapRefs")).equals(publication.receipt().gapRefs())
        || !completion.get("failureRef").isNull()) {
      throw broken();
    }
  }

  private static CodeStructureGraphDraft parseDraft(JsonNode body) {
    requireObjectFields(
        body,
        Set.of(
            "graphKind",
            "graphId",
            "snapshotId",
            "applicationProfileId",
            "graphProfileRef",
            "entryIds",
            "nodes",
            "edges",
            "provenanceDrafts",
            "coverage"));
    return new CodeStructureGraphDraft(
        CodeStructureGraphDraft.SCHEMA_VERSION,
        ProgramGraphKind.valueOf(text(body, "graphKind")),
        id(body, "graphId"),
        text(body, "snapshotId"),
        id(body, "applicationProfileId"),
        reference(body.get("graphProfileRef")),
        ids(body.get("entryIds")),
        nodes(body.get("nodes")),
        edges(body.get("edges")),
        provenance(body.get("provenanceDrafts")),
        coverage(body.get("coverage")));
  }

  private static List<DraftProgramNode> nodes(JsonNode values) {
    return objects(values).stream()
        .map(
            value -> {
              requireObjectFields(
                  value,
                  Set.of(
                      "nodeId", "kind", "canonicalValue", "owningEntryIds", "evidenceDraftRefs"));
              return new DraftProgramNode(
                  id(value, "nodeId"),
                  ProgramNodeKind.valueOf(text(value, "kind")),
                  text(value, "canonicalValue"),
                  ids(value.get("owningEntryIds")),
                  ids(value.get("evidenceDraftRefs")));
            })
        .toList();
  }

  private static List<DraftProgramEdge> edges(JsonNode values) {
    return objects(values).stream()
        .map(
            value -> {
              requireObjectFields(
                  value,
                  Set.of(
                      "edgeId",
                      "kind",
                      "fromNodeId",
                      "toNodeId",
                      "ruleId",
                      "resolution",
                      "guardNodeId",
                      "polarity",
                      "evidenceDraftRefs"));
              return new DraftProgramEdge(
                  id(value, "edgeId"),
                  ProgramEdgeKind.valueOf(text(value, "kind")),
                  id(value, "fromNodeId"),
                  id(value, "toNodeId"),
                  text(value, "ruleId"),
                  ProgramResolution.valueOf(text(value, "resolution")),
                  nullableId(value, "guardNodeId"),
                  nullableText(value, "polarity"),
                  ids(value.get("evidenceDraftRefs")));
            })
        .toList();
  }

  private static List<ProvenanceDraftV1> provenance(JsonNode values) {
    return objects(values).stream()
        .map(
            value -> {
              requireObjectFields(
                  value,
                  Set.of(
                      "provenanceDraftId",
                      "ruleId",
                      "sourceLocator",
                      "sourceFileSha256",
                      "excerptSha256"));
              return new ProvenanceDraftV1(
                  id(value, "provenanceDraftId"),
                  text(value, "ruleId"),
                  locator(value.get("sourceLocator")),
                  new Sha256Digest(text(value, "sourceFileSha256")),
                  new Sha256Digest(text(value, "excerptSha256")));
            })
        .toList();
  }

  private static GraphCoverage coverage(JsonNode value) {
    requireObjectFields(
        value,
        Set.of(
            "candidateElementIds",
            "exactElementIds",
            "gapDispositions",
            "exclusionDispositions",
            "scopeGapIds",
            "closed"));
    List<GraphGapDisposition> gaps =
        objects(value.get("gapDispositions")).stream()
            .map(
                gap -> {
                  requireObjectFields(gap, Set.of("candidateElementId", "gapId"));
                  return new GraphGapDisposition(id(gap, "candidateElementId"), id(gap, "gapId"));
                })
            .toList();
    List<GraphExclusionDisposition> exclusions =
        objects(value.get("exclusionDispositions")).stream()
            .map(
                exclusion -> {
                  requireObjectFields(
                      exclusion, Set.of("candidateElementId", "reasonCode", "evidenceDraftRefs"));
                  return new GraphExclusionDisposition(
                      id(exclusion, "candidateElementId"),
                      text(exclusion, "reasonCode"),
                      ids(exclusion.get("evidenceDraftRefs")));
                })
            .toList();
    JsonNode closed = value.get("closed");
    if (closed == null || !closed.isBoolean()) {
      throw broken();
    }
    return new GraphCoverage(
        ids(value.get("candidateElementIds")),
        ids(value.get("exactElementIds")),
        gaps,
        exclusions,
        ids(value.get("scopeGapIds")),
        closed.booleanValue());
  }

  private static SourceLocatorV1 locator(JsonNode value) {
    requireObjectFields(
        value,
        Set.of(
            "fileId",
            "path",
            "startByte",
            "endByteExclusive",
            "startLine",
            "startColumn",
            "endLine",
            "endColumn"));
    return new SourceLocatorV1(
        id(value, "fileId"),
        text(value, "path"),
        longValue(value, "startByte"),
        longValue(value, "endByteExclusive"),
        integer(value, "startLine"),
        integer(value, "startColumn"),
        integer(value, "endLine"),
        integer(value, "endColumn"));
  }

  private static ArtifactControls controls(JsonNode value) {
    requireObjectFields(
        value,
        Set.of(
            "toolchainSha256",
            "profileSha256",
            "schemaBundleSha256",
            "promptBundleSha256",
            "artifactPolicyRegistryRef"));
    JsonNode prompt = value.get("promptBundleSha256");
    if (prompt == null || !(prompt.isNull() || prompt.isTextual())) {
      throw broken();
    }
    return new ArtifactControls(
        new Sha256Digest(text(value, "toolchainSha256")),
        new Sha256Digest(text(value, "profileSha256")),
        new Sha256Digest(text(value, "schemaBundleSha256")),
        prompt.isNull() ? null : new Sha256Digest(prompt.textValue()),
        policyReference(value.get("artifactPolicyRegistryRef")));
  }

  private static ArtifactPolicyRegistryReference policyReference(JsonNode value) {
    requireObjectFields(value, Set.of("artifactId", "sha256"));
    return new ArtifactPolicyRegistryReference(
        ArtifactId.parse(text(value, "artifactId")), new Sha256Digest(text(value, "sha256")));
  }

  private static List<ArtifactReference> references(JsonNode value) {
    return objects(value).stream().map(PersistedCodeStructureGraphReader::reference).toList();
  }

  private static ArtifactReference reference(JsonNode value) {
    requireObjectFields(value, Set.of("artifactId", "sha256"));
    return new ArtifactReference(
        ArtifactId.parse(text(value, "artifactId")), new Sha256Digest(text(value, "sha256")));
  }

  private static List<ArtifactReference> expectedUpstream(ProgramGraphInputBasis basis) {
    List<ArtifactReference> values =
        new ArrayList<>(
            List.of(
                basis.sourceInventoryRef(),
                basis.verifiedSnapshotRef(),
                basis.applicationProfileRef(),
                basis.capabilityReportRef(),
                basis.entryPointsRef(),
                basis.mapperCatalogRef(),
                basis.graphProfileRef()));
    values.sort(
        Comparator.comparing(
            value -> value.artifactId().value().getBytes(StandardCharsets.UTF_8),
            PersistedCodeStructureGraphReader::compareUtf8));
    return List.copyOf(values);
  }

  private static List<ArtifactId> ids(JsonNode value) {
    if (value == null || !value.isArray()) {
      throw broken();
    }
    List<ArtifactId> result = new ArrayList<>();
    value.forEach(item -> result.add(ArtifactId.parse(text(item))));
    return List.copyOf(result);
  }

  private static List<String> strings(JsonNode value) {
    if (value == null || !value.isArray()) {
      throw broken();
    }
    List<String> result = new ArrayList<>();
    value.forEach(item -> result.add(text(item)));
    return List.copyOf(result);
  }

  private static List<JsonNode> objects(JsonNode value) {
    if (value == null || !value.isArray()) {
      throw broken();
    }
    List<JsonNode> result = new ArrayList<>();
    value.forEach(
        item -> {
          if (!item.isObject()) {
            throw broken();
          }
          result.add(item);
        });
    return List.copyOf(result);
  }

  private static ArtifactId id(JsonNode object, String field) {
    return ArtifactId.parse(text(object, field));
  }

  private static ArtifactId nullableId(JsonNode object, String field) {
    JsonNode value = object.get(field);
    if (value == null) {
      throw broken();
    }
    return value.isNull() ? null : ArtifactId.parse(text(value));
  }

  private static String text(JsonNode object, String field) {
    JsonNode value = object.get(field);
    return text(value);
  }

  private static String nullableText(JsonNode object, String field) {
    JsonNode value = object.get(field);
    if (value == null) {
      throw broken();
    }
    return value.isNull() ? null : text(value);
  }

  private static String text(JsonNode value) {
    if (value == null || !value.isTextual()) {
      throw broken();
    }
    return value.textValue();
  }

  private static int integer(JsonNode object, String field) {
    JsonNode value = object.get(field);
    if (value == null || !value.canConvertToInt()) {
      throw broken();
    }
    return value.intValue();
  }

  private static long longValue(JsonNode object, String field) {
    JsonNode value = object.get(field);
    if (value == null || !value.canConvertToLong()) {
      throw broken();
    }
    return value.longValue();
  }

  private static void requireObjectFields(JsonNode value, Set<String> expected) {
    if (value == null || !value.isObject()) {
      throw broken();
    }
    Set<String> actual = new LinkedHashSet<>();
    Iterator<String> fields = value.fieldNames();
    fields.forEachRemaining(actual::add);
    if (!actual.equals(expected)) {
      throw broken();
    }
  }

  private static int compareUtf8(byte[] first, byte[] second) {
    for (int index = 0; index < Math.min(first.length, second.length); index++) {
      int comparison =
          Integer.compare(Byte.toUnsignedInt(first[index]), Byte.toUnsignedInt(second[index]));
      if (comparison != 0) {
        return comparison;
      }
    }
    return Integer.compare(first.length, second.length);
  }

  private static GraphReferenceException broken() {
    return new GraphReferenceException();
  }

  static final class VerifiedReopenedCodeStructureGraph implements ReopenedCodeStructureGraph {

    private final CodeStructureGraphDraftReference reference;
    private final ArtifactReference payloadRef;
    private final CodeStructureGraphDraft draft;
    private final ProgramGraphInputBasis basis;

    private VerifiedReopenedCodeStructureGraph(
        CodeStructureGraphDraftReference reference,
        ArtifactReference payloadRef,
        CodeStructureGraphDraft draft,
        ProgramGraphInputBasis basis) {
      this.reference = reference;
      this.payloadRef = payloadRef;
      this.draft = draft;
      this.basis = basis;
    }

    @Override
    public CodeStructureGraphDraftReference reference() {
      return reference;
    }

    @Override
    public ArtifactReference payloadRef() {
      return payloadRef;
    }

    @Override
    public CodeStructureGraphDraft draft() {
      return draft;
    }

    @Override
    public ProgramGraphInputBasis basis() {
      return basis;
    }
  }
}
